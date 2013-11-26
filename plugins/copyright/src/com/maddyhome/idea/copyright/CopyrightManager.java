/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maddyhome.idea.copyright;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.UniqueNameGenerator;
import com.maddyhome.idea.copyright.actions.UpdateCopyrightProcessor;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import com.maddyhome.idea.copyright.util.NewFileTracker;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "CopyrightManager",
       storages = {@Storage(file = StoragePathMacros.PROJECT_FILE),
                   @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/copyright/", scheme = StorageScheme.DIRECTORY_BASED,
                            stateSplitter = CopyrightManager.CopyrightStateSplitter.class)})
public class CopyrightManager extends AbstractProjectComponent implements JDOMExternalizable, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#" + CopyrightManager.class.getName());
  @Nullable
  private CopyrightProfile myDefaultCopyright = null;
  private final LinkedHashMap<String, String> myModule2Copyrights = new LinkedHashMap<String, String>();
  private final Map<String, CopyrightProfile> myCopyrights = new HashMap<String, CopyrightProfile>();
  private final Options myOptions = new Options();

  public CopyrightManager(@NotNull Project project,
                          @NotNull final EditorFactory editorFactory,
                          @NotNull final Application application,
                          @NotNull final FileDocumentManager fileDocumentManager,
                          @NotNull final FileTypeUtil fileTypeUtil,
                          @NotNull final ProjectRootManager projectRootManager,
                          @NotNull final PsiManager psiManager,
                          @NotNull StartupManager startupManager) {
    super(project);
    if (!myProject.isDefault()) {
      final NewFileTracker newFileTracker = NewFileTracker.getInstance();
      Disposer.register(myProject, new Disposable() {
        @Override
        public void dispose() {
          newFileTracker.clear();
        }
      });
      startupManager.runWhenProjectIsInitialized(new Runnable() {
        @Override
        public void run() {
          DocumentListener listener = new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent e) {
              final Document document = e.getDocument();
              final VirtualFile virtualFile = fileDocumentManager.getFile(document);
              if (virtualFile == null) return;
              final Module module = projectRootManager.getFileIndex().getModuleForFile(virtualFile);
              if (module == null) return;
              if (!newFileTracker.poll(virtualFile)) return;
              if (!fileTypeUtil.isSupportedFile(virtualFile)) return;
              final PsiFile file = psiManager.findFile(virtualFile);
              if (file == null) return;
              application.invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (myProject.isDisposed()) return;
                  if (file.isValid() && file.isWritable()) {
                    final CopyrightProfile opts = getCopyrightOptions(file);
                    if (opts != null) {
                      new UpdateCopyrightProcessor(myProject, module, file).run();
                    }
                  }
                }
              }, ModalityState.NON_MODAL, myProject.getDisposed());
            }
          };
          editorFactory.getEventMulticaster().addDocumentListener(listener, myProject);
        }
      });
    }
  }

  @NonNls
  private static final String COPYRIGHT = "copyright";
  @NonNls
  private static final String MODULE2COPYRIGHT = "module2copyright";
  @NonNls
  private static final String ELEMENT = "element";
  @NonNls
  private static final String MODULE = "module";
  @NonNls
  private static final String DEFAULT = "default";

  public static CopyrightManager getInstance(Project project) {
    return project.getComponent(CopyrightManager.class);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "CopyrightManager";
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    clearCopyrights();
    final Element module2copyright = element.getChild(MODULE2COPYRIGHT);
    if (module2copyright != null) {
      for (Object o : module2copyright.getChildren(ELEMENT)) {
        final Element el = (Element)o;
        final String moduleName = el.getAttributeValue(MODULE);
        final String copyrightName = el.getAttributeValue(COPYRIGHT);
        myModule2Copyrights.put(moduleName, copyrightName);
      }
    }
    for (Object o : element.getChildren(COPYRIGHT)) {
      final CopyrightProfile copyrightProfile = new CopyrightProfile();
      copyrightProfile.readExternal((Element)o);
      myCopyrights.put(copyrightProfile.getName(), copyrightProfile);
    }
    myDefaultCopyright = myCopyrights.get(element.getAttributeValue(DEFAULT));
    myOptions.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    for (CopyrightProfile copyright : myCopyrights.values()) {
      final Element copyrightElement = new Element(COPYRIGHT);
      copyright.writeExternal(copyrightElement);
      element.addContent(copyrightElement);
    }
    final Element map = new Element(MODULE2COPYRIGHT);
    for (String moduleName : myModule2Copyrights.keySet()) {
      final Element setting = new Element(ELEMENT);
      setting.setAttribute(MODULE, moduleName);
      setting.setAttribute(COPYRIGHT, myModule2Copyrights.get(moduleName));
      map.addContent(setting);
    }
    element.addContent(map);
    element.setAttribute(DEFAULT, myDefaultCopyright != null ? myDefaultCopyright.getName() : "");
    myOptions.writeExternal(element);
  }


  @Override
  public Element getState() {
    try {
      final Element e = new Element("settings");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  @Override
  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public Map<String, String> getCopyrightsMapping() {
    return myModule2Copyrights;
  }

  public void setDefaultCopyright(@Nullable CopyrightProfile copyright) {
    myDefaultCopyright = copyright;
  }

  @Nullable
  public CopyrightProfile getDefaultCopyright() {
    return myDefaultCopyright;
  }

  public void addCopyright(CopyrightProfile copyrightProfile) {
    myCopyrights.put(copyrightProfile.getName(), copyrightProfile);
  }

  public void removeCopyright(CopyrightProfile copyrightProfile) {
    myCopyrights.values().remove(copyrightProfile);
    for (Iterator<String> it = myModule2Copyrights.keySet().iterator(); it.hasNext();) {
      final String profileName = myModule2Copyrights.get(it.next());
      if (profileName.equals(copyrightProfile.getName())) {
        it.remove();
      }
    }
  }

  public void clearCopyrights() {
    myDefaultCopyright = null;
    myCopyrights.clear();
    myModule2Copyrights.clear();
  }

  public void mapCopyright(String scopeName, String copyrightProfileName) {
    myModule2Copyrights.put(scopeName, copyrightProfileName);
  }

  public void unmapCopyright(String scopeName) {
    myModule2Copyrights.remove(scopeName);
  }

  public Collection<CopyrightProfile> getCopyrights() {
    return myCopyrights.values();
  }

  public boolean hasAnyCopyrights() {
    return myDefaultCopyright != null || !myModule2Copyrights.isEmpty();
  }

  @Nullable
  public CopyrightProfile getCopyrightOptions(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || myOptions.getOptions(virtualFile.getFileType().getName()).getFileTypeOverride() == LanguageOptions.NO_COPYRIGHT) return null;
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(myProject);
    for (String scopeName : myModule2Copyrights.keySet()) {
      final NamedScope namedScope = validationManager.getScope(scopeName);
      if (namedScope != null) {
        final PackageSet packageSet = namedScope.getValue();
        if (packageSet != null) {
          if (packageSet.contains(file, validationManager)) {
            final CopyrightProfile profile = myCopyrights.get(myModule2Copyrights.get(scopeName));
            if (profile != null) {
              return profile;
            }
          }
        }
      }
    }
    return myDefaultCopyright != null ? myDefaultCopyright : null;
  }

  public Options getOptions() {
    return myOptions;
  }

  public void replaceCopyright(String displayName, CopyrightProfile copyrightProfile) {
    if (myDefaultCopyright != null && Comparing.strEqual(myDefaultCopyright.getName(), displayName)) {
      myDefaultCopyright = copyrightProfile;
    }
    myCopyrights.remove(displayName);
    addCopyright(copyrightProfile);
  }

  public static class CopyrightStateSplitter implements StateSplitter {
    @Override
    public List<Pair<Element, String>> splitState(Element e) {
      final UniqueNameGenerator generator = new UniqueNameGenerator();
      final List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();

      final Element[] elements = JDOMUtil.getElements(e);
      for (Element element : elements) {
        if (element.getName().equals("copyright")) {
          element.detach();

          String profileName = null;
          final Element[] options = JDOMUtil.getElements(element);
          for (Element option : options) {
            if (option.getName().equals("option") && option.getAttributeValue("name").equals("myName")) {
              profileName = option.getAttributeValue("value");
            }
          }

          assert profileName != null;

          final String name = generator.generateUniqueName(FileUtil.sanitizeFileName(profileName)) + ".xml";
          result.add(new Pair<Element, String>(element, name));
        }
      }
      result.add(new Pair<Element, String>(e, generator.generateUniqueName("profiles_settings") + ".xml"));
      return result;
    }

    @Override
    public void mergeStatesInto(Element target, Element[] elements) {
      for (Element element : elements) {
        if (element.getName().equals("copyright")) {
          element.detach();
          target.addContent(element);
        }
        else {
          final Element[] states = JDOMUtil.getElements(element);
          for (Element state : states) {
            state.detach();
            target.addContent(state);
          }
          for (Object attr : element.getAttributes()) {
            target.setAttribute((Attribute)((Attribute)attr).clone());
          }
        }
      }
    }
  }
}
