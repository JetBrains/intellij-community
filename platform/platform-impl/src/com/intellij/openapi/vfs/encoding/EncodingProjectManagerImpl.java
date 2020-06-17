// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.impl.LightFilePointer;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.ui.GuiUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@State(name = "Encoding", storages = @Storage("encodings.xml"))
public final class EncodingProjectManagerImpl extends EncodingProjectManager implements PersistentStateComponent<Element>, Disposable {
  @NonNls private static final String PROJECT_URL = "PROJECT";
  private final Project myProject;
  private final EncodingManagerImpl myIdeEncodingManager;
  private boolean myNative2AsciiForPropertiesFiles;
  private Charset myDefaultCharsetForPropertiesFiles;
  private @Nullable Charset myDefaultConsoleCharset;
  private final SimpleModificationTracker myModificationTracker = new SimpleModificationTracker();
  private BOMForNewUTF8Files myBomForNewUtf8Files = BOMForNewUTF8Files.NEVER;
  private final Map<VirtualFilePointer, Charset> myMapping = ConcurrentCollectionFactory.createMap(
    new TObjectHashingStrategy<VirtualFilePointer>() {
      @Override
      public int computeHashCode(VirtualFilePointer pointer) {
        // TODO !! hashCode is unstable - VirtualFilePointer URL can change
        return FileUtil.PATH_HASHING_STRATEGY.computeHashCode(pointer.getUrl());
      }

      @Override
      public boolean equals(VirtualFilePointer o1, VirtualFilePointer o2) {
        return FileUtil.PATH_HASHING_STRATEGY.equals(o1.getUrl(), o2.getUrl());
      }
    });
  private volatile Charset myProjectCharset;

  public EncodingProjectManagerImpl(@NotNull Project project) {
    myProject = project;
    myIdeEncodingManager = (EncodingManagerImpl)EncodingManager.getInstance();
  }

  static final class EncodingProjectManagerStartUpActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      GuiUtils.invokeLaterIfNeeded(() -> {
        ((EncodingProjectManagerImpl)getInstance(project)).reloadAlreadyLoadedDocuments();
      }, ModalityState.NON_MODAL, project.getDisposed());
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public Element getState() {
    Element element = new Element("x");
    if (!myMapping.isEmpty()) {
      List<Map.Entry<VirtualFilePointer, Charset>> mappings = new ArrayList<>(myMapping.entrySet());
      ContainerUtil.quickSort(mappings, Comparator.comparing(e -> e.getKey().getUrl()));
      for (Map.Entry<VirtualFilePointer, Charset> mapping : mappings) {
        VirtualFilePointer file = mapping.getKey();
        Charset charset = mapping.getValue();
        Element child = new Element("file");
        element.addContent(child);
        child.setAttribute("url", file.getUrl());
        child.setAttribute("charset", charset.name());
      }
    }
    if (myProjectCharset != null) {
      Element child = new Element("file");
      element.addContent(child);
      child.setAttribute("url", PROJECT_URL);
      child.setAttribute("charset", myProjectCharset.name());
    }

    if (myNative2AsciiForPropertiesFiles) {
      element.setAttribute("native2AsciiForPropertiesFiles", Boolean.toString(true));
    }

    if (myDefaultCharsetForPropertiesFiles != null) {
      element.setAttribute("defaultCharsetForPropertiesFiles", myDefaultCharsetForPropertiesFiles.name());
    }
    if (myDefaultConsoleCharset != null) {
      element.setAttribute("defaultCharsetForConsole", myDefaultConsoleCharset.name());
    }
    if (myBomForNewUtf8Files != BOMForNewUTF8Files.NEVER) {
      element.setAttribute("addBOMForNewFiles", myBomForNewUtf8Files.name);
    }

    return element;
  }

  @Override
  public void loadState(@NotNull Element element) {
    myMapping.clear();
    List<Element> files = element.getChildren("file");
    if (!files.isEmpty()) {
      Map<VirtualFilePointer, Charset> mapping = new THashMap<>();
      for (Element fileElement : files) {
        String url = fileElement.getAttributeValue("url");
        String charsetName = fileElement.getAttributeValue("charset");
        Charset charset = CharsetToolkit.forName(charsetName);
        if (charset == null) {
          continue;
        }

        if (PROJECT_URL.equals(url)) {
          myProjectCharset = charset;
        }
        else if (url != null) {
          VirtualFilePointer file = VirtualFilePointerManager.getInstance().create(url, this, null);
          mapping.put(file, charset);
        }
      }
      myMapping.putAll(mapping);
    }

    myNative2AsciiForPropertiesFiles = Boolean.parseBoolean(element.getAttributeValue("native2AsciiForPropertiesFiles"));
    myDefaultCharsetForPropertiesFiles = CharsetToolkit.forName(element.getAttributeValue("defaultCharsetForPropertiesFiles"));
    myDefaultConsoleCharset = CharsetToolkit.forName(element.getAttributeValue("defaultCharsetForConsole"));
    myBomForNewUtf8Files = BOMForNewUTF8Files.getByNameOrDefault(element.getAttributeValue("addBOMForNewFiles"));

    myModificationTracker.incModificationCount();
  }

  private void reloadAlreadyLoadedDocuments() {
    if (myMapping.isEmpty()) {
      return;
    }

    FileDocumentManagerImpl fileDocumentManager = (FileDocumentManagerImpl)FileDocumentManager.getInstance();
    for (VirtualFilePointer pointer : myMapping.keySet()) {
      VirtualFile file = pointer.getFile();
      Document cachedDocument = file == null ? null : fileDocumentManager.getCachedDocument(file);
      if (cachedDocument != null) {
        // reload document in the right encoding if someone sneaky (you, BreakpointManager) managed to load the document before project opened
        reload(file, myProject, fileDocumentManager);
      }
    }
  }

  @Override
  @Nullable
  public Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults) {
    VirtualFile parent = virtualFile;
    while (parent != null) {
      Charset charset = myMapping.get(new LightFilePointer(parent.getUrl()));
      if (charset != null || !useParentDefaults) return charset;
      parent = parent.getParent();
    }

    return getDefaultCharset();
  }

  @NotNull
  public ModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  @Override
  public void setEncoding(@Nullable final VirtualFile virtualFileOrDir, @Nullable final Charset charset) {
    Charset oldCharset;

    if (virtualFileOrDir == null) {
      oldCharset = myProjectCharset;
      myProjectCharset = charset;
    }
    else {
      VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(virtualFileOrDir, this, null);
      if (charset == null) {
        oldCharset = myMapping.remove(pointer);
      }
      else {
        oldCharset = myMapping.put(pointer, charset);
      }
    }

    if (!Comparing.equal(oldCharset, charset) || virtualFileOrDir != null && !Comparing.equal(virtualFileOrDir.getCharset(), charset)) {
      myModificationTracker.incModificationCount();
      if (virtualFileOrDir != null) {
        virtualFileOrDir.setCharset(virtualFileOrDir.getBOM() == null ? charset : null);
      }
      reloadAllFilesUnder(virtualFileOrDir);
    }
  }

  private static void clearAndReload(@NotNull VirtualFile virtualFileOrDir, @NotNull Project project) {
    virtualFileOrDir.setCharset(null);
    reload(virtualFileOrDir, project, (FileDocumentManagerImpl)FileDocumentManager.getInstance());
  }

  private static void reload(@NotNull VirtualFile virtualFile, @NotNull Project project, @NotNull FileDocumentManagerImpl documentManager) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectLocator.computeWithPreferredProject(virtualFile, project, ()-> {
        documentManager.contentsChanged(new VFileContentChangeEvent(null, virtualFile, 0, 0, false));
        return null;
      });
    });
  }

  @Override
  @NotNull
  public Collection<Charset> getFavorites() {
    Set<Charset> result = widelyKnownCharsets();
    result.addAll(myMapping.values());
    result.add(getDefaultCharset());
    return result;
  }

  @NotNull
  static Set<Charset> widelyKnownCharsets() {
    Set<Charset> result = new HashSet<>();
    result.add(StandardCharsets.UTF_8);
    result.add(CharsetToolkit.getDefaultSystemCharset());
    result.add(CharsetToolkit.getPlatformCharset());
    result.add(StandardCharsets.UTF_16);
    result.add(StandardCharsets.ISO_8859_1);
    result.add(StandardCharsets.US_ASCII);
    result.add(EncodingManager.getInstance().getDefaultCharset());
    result.add(EncodingManager.getInstance().getDefaultCharsetForPropertiesFiles(null));
    result.remove(null);
    return result;
  }

  /**
   * @return readonly map of current mappings. to modify mappings use {@link #setMapping(Map)}
   */
  @NotNull
  public Map<? extends VirtualFile, ? extends Charset> getAllMappings() {
    return myMapping.entrySet().stream()
      .map(e -> Pair.create(e.getKey().getFile(), e.getValue()))
      .filter(e -> e.getFirst() != null)
      .collect(Collectors.toMap(p -> p.getFirst(), p -> p.getSecond(), (c1, c2) -> c1));
  }

  public void setMapping(@NotNull Map<? extends VirtualFile, ? extends Charset> mapping) {
    ApplicationManager.getApplication().assertIsWriteThread();
    FileDocumentManager.getInstance().saveAllDocuments();  // consider all files as unmodified
    final Map<VirtualFilePointer, Charset> newMap = new THashMap<>(mapping.size());
    final Map<VirtualFilePointer, Charset> oldMap = new THashMap<>(myMapping);

    // ChangeFileEncodingAction should not start progress "reload files..."
    suppressReloadDuring(() -> {
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      for (Map.Entry<? extends VirtualFile, ? extends Charset> entry : mapping.entrySet()) {
        VirtualFile virtualFile = entry.getKey();
        Charset charset = entry.getValue();
        if (charset == null) throw new IllegalArgumentException("Null charset for " + virtualFile + "; mapping: " + mapping);
        if (virtualFile == null) {
          myProjectCharset = charset;
        }
        else {
          if (!fileIndex.isInContent(virtualFile)) continue;
          VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(virtualFile, this, null);

          if (!virtualFile.isDirectory() && !Comparing.equal(charset, oldMap.get(pointer))) {
            Document document;
            byte[] bytes;
            try {
              document = FileDocumentManager.getInstance().getDocument(virtualFile);
              if (document == null) throw new IOException();
              bytes = virtualFile.contentsToByteArray();
            }
            catch (IOException e) {
              continue;
            }
            // ask whether to reload/convert when in doubt
            boolean changed = new ChangeFileEncodingAction().chosen(document, null, virtualFile, bytes, charset);

            if (!changed) continue;
          }
          newMap.put(pointer, charset);
        }
      }
    });

    myMapping.clear();
    myMapping.putAll(newMap);

    final Set<VirtualFilePointer> changed = new HashSet<>(oldMap.keySet());
    for (Map.Entry<VirtualFilePointer, Charset> entry : newMap.entrySet()) {
      VirtualFilePointer file = entry.getKey();
      Charset charset = entry.getValue();
      Charset oldCharset = oldMap.get(file);
      if (Comparing.equal(oldCharset, charset)) {
        changed.remove(file);
      }
    }

    Set<VirtualFilePointer> added = new HashSet<>(newMap.keySet());
    added.removeAll(oldMap.keySet());

    Set<VirtualFilePointer> removed = new HashSet<>(oldMap.keySet());
    removed.removeAll(newMap.keySet());

    changed.addAll(added);
    changed.addAll(removed);
    changed.remove(null);

    if (!changed.isEmpty()) {
      Processor<VirtualFile> reloadProcessor = createChangeCharsetProcessor(myProject);
      tryStartReloadWithProgress(() -> {
        Set<VirtualFile> processed = new THashSet<>();
        next:
        for (VirtualFilePointer changedFilePointer : changed) {
          VirtualFile changedFile = changedFilePointer.getFile();
          if (changedFile == null) continue;
          for (VirtualFile processedFile : processed) {
            if (VfsUtilCore.isAncestor(processedFile, changedFile, false)) continue next;
          }
          processSubFiles(changedFile, reloadProcessor);
          processed.add(changedFile);
        }
      });
    }

    myModificationTracker.incModificationCount();
  }

  @NotNull
  private static Processor<VirtualFile> createChangeCharsetProcessor(@NotNull Project project) {
    return file -> {
      if (file.isDirectory()) {
        return true;
      }
      if (!(file instanceof VirtualFileSystemEntry)) return false;
      Document cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file);
      if (cachedDocument == null) {
        if (file.isCharsetSet()) {
          file.setCharset(null, null, false);
        }
        return true;
      }
      ProgressManager.progress(IdeBundle.message("progress.text.reloading.files"), file.getPresentableUrl());
      TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> clearAndReload(file, project));
      return true;
    };
  }

  private boolean processSubFiles(@Nullable("null means all in the project") VirtualFile file, @NotNull final Processor<? super VirtualFile> processor) {
    if (file == null) {
      for (VirtualFile virtualFile : ProjectRootManager.getInstance(myProject).getContentRoots()) {
        if (!processSubFiles(virtualFile, processor)) return false;
      }
      return true;
    }

    return VirtualFileVisitor.CONTINUE == VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull final VirtualFile file) {
        return processor.process(file);
      }
    });
  }

  //retrieves encoding for the Project node
  @Override
  @NotNull
  public Charset getDefaultCharset() {
    Charset charset = myProjectCharset;
    // if the project charset was not specified, use the IDE encoding, save this back
    return charset == null ? myIdeEncodingManager.getDefaultCharset() : charset;
  }

  @Nullable
  public Charset getConfiguredDefaultCharset() {
    return myProjectCharset;
  }

  private static final ThreadLocal<Boolean> SUPPRESS_RELOAD = new ThreadLocal<>();
  static void suppressReloadDuring(@NotNull Runnable action) {
    Boolean old = SUPPRESS_RELOAD.get();
    try {
      SUPPRESS_RELOAD.set(Boolean.TRUE);
      action.run();
    }
    finally {
      SUPPRESS_RELOAD.set(old);
    }
  }

  private void tryStartReloadWithProgress(@NotNull final Runnable reloadAction) {
    Boolean suppress = SUPPRESS_RELOAD.get();
    if (suppress == Boolean.TRUE) return;
    FileDocumentManager.getInstance().saveAllDocuments();  // consider all files as unmodified
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> suppressReloadDuring(reloadAction),
                                                                      IdeBundle.message("progress.title.reload.files"), false, myProject);
  }

  private void reloadAllFilesUnder(@Nullable final VirtualFile root) {
    tryStartReloadWithProgress(() -> processSubFiles(root, file -> {
      if (!(file instanceof VirtualFileSystemEntry)) return true;
      Document cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file);
      if (cachedDocument != null) {
        ProgressManager.progress(IdeBundle.message("progress.text.reloading.file"), file.getPresentableUrl());
        TransactionGuard.submitTransaction(myProject, () -> reload(file, myProject, (FileDocumentManagerImpl)FileDocumentManager.getInstance()));
      }
      // for not loaded files deep under project, reset encoding to give them chance re-detect the right one later
      else if (file.isCharsetSet() && !file.equals(root)) {
        file.setCharset(null);
      }
      return true;
    }));
  }

  @Override
  public boolean isNative2Ascii(@NotNull final VirtualFile virtualFile) {
    return FileTypeRegistry.getInstance().isFileOfType(virtualFile, StdFileTypes.PROPERTIES) && myNative2AsciiForPropertiesFiles;
  }

  @Override
  public boolean isNative2AsciiForPropertiesFiles() {
    return myNative2AsciiForPropertiesFiles;
  }

  @Override
  public void setNative2AsciiForPropertiesFiles(final VirtualFile virtualFile, final boolean native2Ascii) {
    if (myNative2AsciiForPropertiesFiles != native2Ascii) {
      myNative2AsciiForPropertiesFiles = native2Ascii;
      myIdeEncodingManager.firePropertyChange(null, PROP_NATIVE2ASCII_SWITCH, !native2Ascii, native2Ascii, myProject);
    }
  }

  @NotNull // empty means system default
  @Override
  public String getDefaultCharsetName() {
    Charset charset = getEncoding(null, false);
    return charset == null ? "" : charset.name();
  }

  @Override
  public void setDefaultCharsetName(@NotNull String name) {
    setEncoding(null, name.isEmpty() ? null : CharsetToolkit.forName(name));
  }

  @Override
  @Nullable
  public Charset getDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile) {
    return myDefaultCharsetForPropertiesFiles;
  }

  @Override
  public void setDefaultCharsetForPropertiesFiles(@Nullable final VirtualFile virtualFile, @Nullable Charset charset) {
    Charset old = myDefaultCharsetForPropertiesFiles;
    if (!Comparing.equal(old, charset)) {
      myDefaultCharsetForPropertiesFiles = charset;
      myIdeEncodingManager.firePropertyChange(null, PROP_PROPERTIES_FILES_ENCODING, old, charset, myProject);
    }
  }

  @Override
  public @NotNull Charset getDefaultConsoleEncoding() {
    return myIdeEncodingManager.getDefaultConsoleEncoding();
  }

  @Override
  @Nullable
  public Charset getCachedCharsetFromContent(@NotNull Document document) {
    return myIdeEncodingManager.getCachedCharsetFromContent(document);
  }

  public enum BOMForNewUTF8Files {
    ALWAYS("with BOM"),
    NEVER("with NO BOM"),
    WINDOWS_ONLY("with BOM under Windows, with no BOM otherwise");

    private final String name;

    BOMForNewUTF8Files(@NotNull String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    @NotNull
    private static BOMForNewUTF8Files getByNameOrDefault(@Nullable String name) {
      if (!StringUtil.isEmpty(name)) {
        for (BOMForNewUTF8Files value : values()) {
          if (value.name.equalsIgnoreCase(name)) {
            return value;
          }
        }
      }
      return NEVER;
    }
  }

  public void setBOMForNewUtf8Files(@NotNull BOMForNewUTF8Files option) {
    myBomForNewUtf8Files = option;
  }

  @NotNull
  BOMForNewUTF8Files getBOMForNewUTF8Files() {
    return myBomForNewUtf8Files;
  }

  @Override
  public boolean shouldAddBOMForNewUtf8File() {
    switch (myBomForNewUtf8Files) {
      case ALWAYS:
        return true;
      case NEVER:
        return false;
      case WINDOWS_ONLY:
        return SystemInfo.isWindows;
      default:
        throw new IllegalStateException(myBomForNewUtf8Files.toString());
    }
  }
}
