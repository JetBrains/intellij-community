// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;

/**
 * author: lesya
 */
public class CheckoutHelper {
  private File myCheckoutLocation;
  private final CvsRootConfiguration myConfiguration;
  private final Component myPanel;
  private CvsElement myElement;

  public CheckoutHelper(CvsRootConfiguration configuration, Component panel) {
    myConfiguration = configuration;
    myPanel = panel;
  }

  public boolean prepareCheckoutData(CvsElement element, boolean useAltCheckoutPath) {
    myElement = element;
    if (!useAltCheckoutPath) {
      if (!requestLocation()) {
        return false;
      }
      if (shouldCreateDirectoryOfTheSameName()) {
        if (!creatingConfirmation()) {
          return false;
        }
      }
    }
    return true;
  }

  private boolean creatingConfirmation() {
    File checkoutDirectory = new File(myCheckoutLocation, myElement.getCheckoutPath());
    if (checkoutDirectory.isDirectory()) return true;
    return MessageDialogBuilder.yesNo(CvsBundle.message("operation.name.check.out.project"),
                                      CvsBundle.message("confirmation.text.directory.will.be.created", checkoutDirectory)).ask(myPanel);
  }

  private boolean shouldCreateDirectoryOfTheSameName() {
    int compareResult =
        new File(myCheckoutLocation.getName()).compareTo(new File(myElement.getCheckoutDirectoryName()));
    return compareResult == 0;
  }

  @Nullable
  public VirtualFile chooseCheckoutLocation(String pathToSuggestedFolder) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(CvsBundle.message("dialog.description.select.a.directory.to.check.out.the.files.to"));
    VirtualFile suggestedCheckoutFolder =
      LocalFileSystem.getInstance().findFileByPath(pathToSuggestedFolder.replace(File.separatorChar, '/'));
    return FileChooser.chooseFile(descriptor, myPanel, null, suggestedCheckoutFolder);
  }

  private boolean requestLocation() {
    VirtualFile virtualFile = chooseCheckoutLocation(myConfiguration.PATH_TO_WORKING_FOLDER);
    if (virtualFile == null) return false;
    myCheckoutLocation = VfsUtil.virtualToIoFile(virtualFile);
    myConfiguration.PATH_TO_WORKING_FOLDER = myCheckoutLocation.getAbsolutePath();
    return true;
  }

  public File getCheckoutLocation() {
    return myCheckoutLocation;
  }
}
