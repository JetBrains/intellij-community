/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import org.jetbrains.plugins.groovy.lang.completion.handlers.ContextSpecificInsertHandler;
import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author ilyas
 */
public class GroovyInsertHandlerAdapter extends DefaultInsertHandler{

  private final ContextSpecificInsertHandler[] myHandlers;
  private final GroovyInsertHandler myGroovyInsertHandler = new GroovyInsertHandler();

  public GroovyInsertHandlerAdapter(ContextSpecificInsertHandler ... handlers) {
    myHandlers = handlers;
  }

  public void handleInsert(CompletionContext context, int startOffset, LookupData data, LookupItem item, boolean signatureSelected, char completionChar) {
    for (ContextSpecificInsertHandler handler : myHandlers) {
      if (handler.isAcceptable(context, startOffset, data, item, signatureSelected, completionChar)) {
        handler.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
        return;
      }
    }
    myGroovyInsertHandler.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
  }
}
