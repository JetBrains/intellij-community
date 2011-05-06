/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.android.sdklib.SdkConstants;
import com.intellij.ide.fileTemplates.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.fileTypes.AndroidIdlFileType;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;

/**
 * @author yole
 */
public class AndroidFileTemplateProvider implements FileTemplateGroupDescriptorFactory {
  @NonNls public static final String REMOTE_INTERFACE_TEMPLATE = "Remote Interface.aidl";
  @NonNls public static final String ANDROID_MANIFEST_TEMPLATE = SdkConstants.FN_ANDROID_MANIFEST_XML;
  @NonNls public static final String VALUE_RESOURCE_FILE_TEMPLATE = "valueResourceFile.xml";
  @NonNls public static final String RESOURCE_FILE_TEMPLATE = "resourceFile.xml";
  @NonNls public static final String LAYOUT_RESOURCE_FILE_TEMPLATE = "layoutResourceFile.xml";
  @NonNls public static final String ACTIVITY = "Activity.java";
  @NonNls public static final String APPLICATION = "Application.java";
  @NonNls public static final String SERVICE = "Service.java";
  @NonNls public static final String BROADCAST_RECEIVER = "Broadcast Receiver.java";

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor("Android", AndroidUtils.ANDROID_ICON);
    group.addTemplate(new FileTemplateDescriptor(ANDROID_MANIFEST_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(VALUE_RESOURCE_FILE_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(RESOURCE_FILE_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(LAYOUT_RESOURCE_FILE_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(ACTIVITY, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(APPLICATION, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(SERVICE, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(BROADCAST_RECEIVER, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(REMOTE_INTERFACE_TEMPLATE, AndroidIdlFileType.ourFileType.getIcon()));
    return group;
  }

  // must be invoked in a write action
  @Nullable
  public static PsiElement createFromTemplate(@NotNull Project project,
                                              @NotNull VirtualFile rootDir,
                                              @NotNull String templateName,
                                              @NotNull String fileName,
                                              @NotNull Properties properties) throws Exception {
    rootDir.refresh(false, false);
    PsiDirectory directory = PsiManager.getInstance(project).findDirectory(rootDir);
    if (directory != null) {
      return createFromTemplate(templateName, fileName, directory, properties);
    }
    return null;
  }


  @Nullable
  public static PsiElement createFromTemplate(@NotNull Project project,
                                        @NotNull VirtualFile rootDir,
                                        @NotNull String templateName,
                                        @NotNull String fileName) throws Exception {
    return createFromTemplate(project, rootDir, templateName, fileName, FileTemplateManager.getInstance().getDefaultProperties());
  }

  public static PsiElement createFromTemplate(String templateName, String fileName, PsiDirectory directory, Properties properties)
    throws Exception {
    FileTemplateManager manager = FileTemplateManager.getInstance();
    FileTemplate template = manager.getJ2eeTemplate(templateName);
    return FileTemplateUtil.createFromTemplate(template, fileName, properties, directory);
  }

  public static PsiElement createFromTemplate(String templateName, String fileName, PsiDirectory directory) throws Exception {
    return createFromTemplate(templateName, fileName, directory, FileTemplateManager.getInstance().getDefaultProperties());
  }

  @NotNull
  public static String getFileNameByNewElementName(@NotNull String name) {
    String extension = FileUtil.getExtension(name);
    if (!extension.equals("xml")) {
      name += ".xml";
    }
    return name;
  }
}
