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

package org.jetbrains.plugins.groovy.lang.completion;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.testcases.action.ActionTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.actions.CodeCompletionAction;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.util.IncorrectOperationException;


import java.io.IOException;
import java.util.*;

/**
 * @author ven
 */
public class ReferenceCompletionTest extends CompletionTestBase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/completion/data/reference";

  protected String myNewDocumentText;

  public ReferenceCompletionTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  protected LookupItem[] getAcceptableItems(CompletionData data) {
    return getAcceptableItemsImpl(data, false, true);
  }
}