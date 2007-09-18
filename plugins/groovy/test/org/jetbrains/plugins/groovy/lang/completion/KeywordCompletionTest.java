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

import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;

/**
 * @author ilyas
 */
public class KeywordCompletionTest extends CompletionTestBase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/completion/data/keyword";

  protected String myNewDocumentText;

  public KeywordCompletionTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  protected LookupItem[] getAcceptableItems(CompletionData data) throws IncorrectOperationException {
    return getAcceptableItemsImpl(data, true, false);
  }

  public static Test suite() {
    return new KeywordCompletionTest();
  }
}