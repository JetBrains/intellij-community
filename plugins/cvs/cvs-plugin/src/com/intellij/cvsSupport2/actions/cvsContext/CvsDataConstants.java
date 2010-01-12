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

/**
 * author: lesya
 *
 * @deprecated use {@link com.intellij.cvsSupport2.actions.cvsContext.CvsDataKeys} instead
 */
@SuppressWarnings({"UnusedDeclaration"})
public interface CvsDataConstants {
  String DELETED_FILE_NAMES = CvsDataKeys.DELETED_FILE_NAMES.getName();
  String FILE_TO_RESTORE = CvsDataKeys.FILE_TO_RESTORE.getName();
  String CVS_LIGHT_FILE = CvsDataKeys.CVS_LIGHT_FILE.getName();
  String CVS_LIGHT_FILES = CvsDataKeys.CVS_LIGHT_FILES.getName();
  String CVS_ENVIRONMENT = CvsDataKeys.CVS_ENVIRONMENT.getName();
  String FILES_TO_ADD = CvsDataKeys.FILES_TO_ADD.getName();
}
