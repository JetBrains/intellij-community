/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 */
@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
public class MacFileChooserDialogImpl implements PathChooserDialog {
  private static final int OK = 1;

  private static final Map<ID, MacFileChooserDialogImpl> ourImplMap = new HashMap<ID, MacFileChooserDialogImpl>(2);

  private final FileChooserDescriptor myChooserDescriptor;
  private final Project myProject;
  private ModalityState myModalityState;
  private Consumer<List<VirtualFile>> myCallback;

  private static final Callback SHOULD_ENABLE_CALLBACK = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public boolean callback(ID self, String selector, ID panel, ID url) {
      try {
        return checkFile(self, url, true);  // allow any directory - ability to select nested directories
      }
      catch (Exception e) {
        return false;
      }
    }
  };

  private static final Callback VALIDATE_URL_CALLBACK = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public boolean callback(ID self, String selector, ID panel, ID url, ID outError) {
      try {
        return checkFile(self, url, false);
      }
      catch (Exception e) {
        if (!ID.NIL.equals(outError)) {
          ID domain = Foundation.nsString(ApplicationNamesInfo.getInstance().getProductName());
          ID dict = Foundation.createDict(new String[]{"NSLocalizedDescription"}, new Object[]{e.getMessage()});
          ID error = Foundation.invoke("NSError", "errorWithDomain:code:userInfo:", domain, 100, dict);
          new Pointer(outError.longValue()).setLong(0, error.longValue());
        }
        return false;
      }
    }
  };

  private static boolean checkFile(@NotNull ID self, ID url, boolean quickCheck) throws Exception {
    MacFileChooserDialogImpl impl = ourImplMap.get(self);
    if (impl == null) {
      return true;  // already removed from the map: the file is likely to be valid since the user was able to select it
    }

    if (url == null || url.intValue() == 0) {
      return false;
    }

    ID filename = Foundation.invoke(url, "path");
    String path = Foundation.toStringViaUTF8(filename);
    if (path == null) {
      return false;
    }

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null || quickCheck && file.isDirectory()) {
      return true;
    }

    if (!impl.myChooserDescriptor.isFileSelectable(file)) {
      return false;
    }

    if (!quickCheck) {
      VirtualFile[] files = {file};
      impl.myChooserDescriptor.validateSelectedFiles(files);
    }

    return true;
  }

  private static final Callback OPEN_PANEL_DID_END = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, String selector, ID openPanelDidEnd, ID returnCode, ID contextInfo) {
      final MacFileChooserDialogImpl impl = ourImplMap.remove(self);

      try {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final IdeMenuBar bar = getMenuBar();
            if (bar != null) {
              bar.enableUpdates();
            }
          }
        });

        final List<String> resultPaths = processResult(returnCode, openPanelDidEnd);
        if (resultPaths.size() > 0) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_MODAL, new Runnable() {
                @Override
                public void run() {
                  final List<VirtualFile> files = getChosenFiles(resultPaths);
                  if (files.size() > 0) {
                    FileChooserUtil.setLastOpenedFile(impl.myProject, files.get(files.size() - 1));
                    impl.myCallback.consume(files);
                  }
                }
              });
            }
          }, impl.myModalityState);
        } else if (impl.myCallback instanceof FileChooser.FileChooserConsumer) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ((FileChooser.FileChooserConsumer)impl.myCallback).cancelled();
            }
          }, impl.myModalityState);
        }
      }
      finally {
        invoke(contextInfo, "setDelegate:", ID.NIL);
        Foundation.cfRelease(self);
        Foundation.cfRelease(contextInfo);
        JDK7WindowReorderingWorkaround.enableReordering();
      }
    }
  };

  @NotNull
  private static List<String> processResult(final ID result, final ID panel) {
    final List<String> resultPaths = new ArrayList<String>();

    if (result != null && OK == result.intValue()) {
      final ID fileNamesArray = invoke(panel, "URLs");
      final ID enumerator = invoke(fileNamesArray, "objectEnumerator");

      while (true) {
        final ID url = invoke(enumerator, "nextObject");
        if (url == null || 0 == url.intValue()) break;

        final ID filename = invoke(url, "path");
        final String path = Foundation.toStringViaUTF8(filename);
        if (path != null) {
          resultPaths.add(path);
        }
      }
    }

    return resultPaths;
  }

  @NotNull
  private static List<VirtualFile> getChosenFiles(final List<String> paths) {
    if (ContainerUtil.isEmpty(paths)) {
      return Collections.emptyList();
    }

    final LocalFileSystem fs = LocalFileSystem.getInstance();
    final List<VirtualFile> files = ContainerUtil.newArrayListWithCapacity(paths.size());
    for (String path : paths) {
      final String vfsPath = FileUtil.toSystemIndependentName(path);
      final VirtualFile file = fs.refreshAndFindFileByPath(vfsPath);
      if (file != null && file.isValid()) {
        files.add(file);
      }
    }

    return files;
  }

  private static final Callback MAIN_THREAD_RUNNABLE = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, String selector, ID toSelect) {
      final ID nsOpenPanel = Foundation.getObjcClass("NSOpenPanel");
      final ID chooser = invoke(nsOpenPanel, "openPanel");
      // Release in OPEN_PANEL_DID_END panel
      Foundation.cfRetain(chooser);

      final FileChooserDescriptor chooserDescriptor = ourImplMap.get(self).myChooserDescriptor;

      invoke(chooser, "setPrompt:", Foundation.nsString("Choose"));
      invoke(chooser, "setCanChooseFiles:", chooserDescriptor.isChooseFiles() || chooserDescriptor.isChooseJars());
      invoke(chooser, "setCanChooseDirectories:", chooserDescriptor.isChooseFolders());
      invoke(chooser, "setAllowsMultipleSelection:", chooserDescriptor.isChooseMultiple());
      invoke(chooser, "setTreatsFilePackagesAsDirectories:", chooserDescriptor.isChooseFolders());
      invoke(chooser, "setResolvesAliases:", false);

      String description = chooserDescriptor.getDescription();
      if (!StringUtil.isEmpty(description)) {
        invoke(chooser, "setMessage:", Foundation.nsString(StringUtil.removeHtmlTags(description)));
      }

      if (Foundation.isClassRespondsToSelector(nsOpenPanel, Foundation.createSelector("setCanCreateDirectories:"))) {
        invoke(chooser, "setCanCreateDirectories:", true);
      }
      else if (Foundation.isClassRespondsToSelector(nsOpenPanel, Foundation.createSelector("_setIncludeNewFolderButton:"))) {
        invoke(chooser, "_setIncludeNewFolderButton:", true);
      }

      boolean showHidden = chooserDescriptor.isShowHiddenFiles() || Registry.is("ide.mac.file.chooser.show.hidden.files");
      if (showHidden) {
        if (Foundation.isClassRespondsToSelector(nsOpenPanel, Foundation.createSelector("setShowsHiddenFiles:"))) {
          invoke(chooser, "setShowsHiddenFiles:", true);
        }
      }

      invoke(chooser, "setDelegate:", self);

      ID directory = null;
      ID file = null;
      final String toSelectPath = toSelect == null || toSelect.intValue() == 0 ? null : Foundation.toStringViaUTF8(toSelect);
      if (toSelectPath != null) {
        final File toSelectFile = new File(toSelectPath);
        if (toSelectFile.isDirectory()) {
          directory = toSelect;
        }
        else if (toSelectFile.isFile()) {
          directory = Foundation.nsString(toSelectFile.getParent());
          file = Foundation.nsString(toSelectFile.getName());
        }
      }

      ID types = null;
      if (!chooserDescriptor.isChooseFiles() && chooserDescriptor.isChooseJars()) {
        types = invoke("NSArray", "arrayWithObjects:", Foundation.nsString("jar"), Foundation.nsString("zip"), null);
      }

      final Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (activeWindow != null) {
        String activeWindowTitle = null;
        if (activeWindow instanceof Frame) {
          activeWindowTitle = ((Frame)activeWindow).getTitle();
        }
        else if (activeWindow instanceof JDialog) {
          activeWindowTitle = ((JDialog)activeWindow).getTitle();
        }

        final ID focusedWindow = MacUtil.findWindowForTitle(activeWindowTitle);
        if (focusedWindow != null) {
          invoke(chooser, "beginSheetForDirectory:file:types:modalForWindow:modalDelegate:didEndSelector:contextInfo:",
                 directory, file, types, focusedWindow, self, Foundation.createSelector("openPanelDidEnd:returnCode:contextInfo:"), chooser);
        }
      }
    }
  };

  static {
    ID delegate = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSOpenPanelDelegate_");
    addFoundationMethod(delegate, "showOpenPanel:", MAIN_THREAD_RUNNABLE, "v*");
    addFoundationMethod(delegate, "openPanelDidEnd:returnCode:contextInfo:", OPEN_PANEL_DID_END, "v*i");
    addFoundationMethod(delegate, "panel:shouldEnableURL:", SHOULD_ENABLE_CALLBACK, "B@@");
    if (SystemInfo.isMacOSSnowLeopard) {
      addFoundationMethod(delegate, "panel:validateURL:error:", VALIDATE_URL_CALLBACK, "B@@o");
    }
    Foundation.registerObjcClassPair(delegate);
  }

  private static void addFoundationMethod(@NotNull ID delegate, @NotNull String selector, @NotNull Callback callback, @NotNull String types) {
    if (!Foundation.addMethod(delegate, Foundation.createSelector(selector), callback, types)) {
      throw new RuntimeException("Unable to add method " + selector + " to objective-c delegate class!");
    }
  }

  public MacFileChooserDialogImpl(@NotNull final FileChooserDescriptor chooserDescriptor, final Project project) {
    myChooserDescriptor = chooserDescriptor;
    myProject = project;
  }

  @Override
  public void choose(@Nullable final VirtualFile toSelect, @NotNull final Consumer<List<VirtualFile>> callback) {
    ExtensionsInitializer.initialize();

    myCallback = callback;
    myModalityState = ModalityState.current();

    final VirtualFile lastOpenedFile = FileChooserUtil.getLastOpenedFile(myProject);
    final VirtualFile selectFile = FileChooserUtil.getFileToSelect(myChooserDescriptor, myProject, toSelect, lastOpenedFile);
    final String selectPath = selectFile != null ? FileUtil.toSystemDependentName(selectFile.getPath()) : null;

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        showNativeChooserAsSheet(MacFileChooserDialogImpl.this, selectPath);
      }
    });
  }

  @Nullable
  private static IdeMenuBar getMenuBar() {
    Window cur = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();

    while (cur != null) {
      if (cur instanceof JFrame) {
        final JMenuBar menuBar = ((JFrame)cur).getJMenuBar();
        if (menuBar instanceof IdeMenuBar) {
          return (IdeMenuBar)menuBar;
        }
      }
      cur = cur.getOwner();
    }
    return null;
  }

  private static void showNativeChooserAsSheet(@NotNull final MacFileChooserDialogImpl impl, @Nullable final String toSelect) {
    final IdeMenuBar bar = getMenuBar();
    if (bar != null) {
      bar.disableUpdates();
    }

    final ID delegate = invoke(Foundation.getObjcClass("NSOpenPanelDelegate_"), "new");
    ourImplMap.put(delegate, impl);

    final ID select = toSelect == null ? null : Foundation.nsString(toSelect);
    JDK7WindowReorderingWorkaround.disableReordering();
    invoke(delegate, "performSelectorOnMainThread:withObject:waitUntilDone:", Foundation.createSelector("showOpenPanel:"), select, false);
  }

  private static ID invoke(@NotNull final String className, @NotNull final String selector, Object... args) {
    return invoke(Foundation.getObjcClass(className), selector, args);
  }

  private static ID invoke(@NotNull final ID id, @NotNull final String selector, Object... args) {
    return Foundation.invoke(id, Foundation.createSelector(selector), args);
  }

  /** This class is intended to force extensions initialization on EDT thread (IDEA-107271) */
  private static class ExtensionsInitializer {
    private ExtensionsInitializer() {}
    private static boolean initialized;
    private static void initialize() {
      if (initialized) return;
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          Extensions.getExtensions(ProjectOpenProcessor.EXTENSION_POINT_NAME);
        }
      });
      initialized = true;
    }
  }
}
