/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author ilyas
 */
public interface Icons {

  public static final Icon FILE_TYPE = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/groovy_fileType.png");
  public static final Icon SMALLEST = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/groovy_16x16.png");
  public static final Icon CLAZZ = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/class.png");
  public static final Icon ABSTRACT = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/abstractClass.png");
  public static final Icon INTERFACE = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/interface.png");
  public static final Icon ANNOTAION = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/annotationtype.png");
  public static final Icon ENUM = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/enum.png");
  public static final Icon PROPERTY = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/property.png");
  public static final Icon GSP_FILE_TYPE = IconLoader.findIcon("/org/jetbrains/plugins/groovy/images/gsp_logo.png");
}
