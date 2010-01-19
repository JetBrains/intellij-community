/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileTypes.FileType;

/**
 * @author yole
 */
public class FileChooserKeys {
  public static final DataKey<FileType> NEW_FILE_TYPE = DataKey.create("NewFileType");
  public static final DataKey<String> NEW_FILE_TEMPLATE_TEXT = DataKey.create("NewFileTemplateText");
  public static final DataKey<Boolean> DELETE_ACTION_AVAILABLE = DataKey.create("FileChooserDeleteActionAvailable");
}