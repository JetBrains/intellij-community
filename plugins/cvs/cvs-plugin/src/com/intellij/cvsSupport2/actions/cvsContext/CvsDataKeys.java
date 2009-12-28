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
package com.intellij.cvsSupport2.actions.cvsContext;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.openapi.actionSystem.DataKey;

import java.util.Collection;

public interface CvsDataKeys {
  DataKey<Collection<String>> DELETED_FILE_NAMES = DataKey.create("DeletedFileNames");
  DataKey<String> FILE_TO_RESTORE = DataKey.create("FileToRestore");
  DataKey<CvsLightweightFile> CVS_LIGHT_FILE = DataKey.create("CVS_LIGHT_FILE");
  DataKey<CvsLightweightFile[]> CVS_LIGHT_FILES = DataKey.create("CVS_LIGHT_FILES");
  DataKey<CvsEnvironment> CVS_ENVIRONMENT = DataKey.create("CVS_ENVIRONMENT");
  DataKey<Collection<AddedFileInfo>> FILES_TO_ADD = DataKey.create("FilesToAdd");
}
