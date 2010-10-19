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
package org.jetbrains.android.fileTypes;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidIdlFileImpl extends PsiFileBase {
  private final FileType myFileType;

  public AndroidIdlFileImpl(FileViewProvider viewProvider) {
    //super(AndroidIdlParserDefinition.AIDL_FILE_ELEMENT_TYPE, AndroidIdlParserDefinition.AIDL_TEXT, viewProvider);
    super(viewProvider, AndroidIdlFileType.ourFileType.getLanguage());
    myFileType = viewProvider.getVirtualFile().getFileType();
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  /*public void accept(@NotNull final PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }*/
}
