/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* Created by Max Medvedev on 21/03/14
*/
class QuickFixActionRegistrarAdapter implements QuickFixActionRegistrar {
  private final HighlightInfo myInfo;
  private HighlightDisplayKey myKey;

  public QuickFixActionRegistrarAdapter(@Nullable HighlightInfo info, HighlightDisplayKey displayKey) {
    myInfo = info;
    myKey = displayKey;
  }

  @Override
  public void register(@NotNull IntentionAction action) {
    myKey = GrUnresolvedAccessInspection.findDisplayKey();
    QuickFixAction.registerQuickFixAction(myInfo, action, myKey);
  }

  @Override
  public void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, HighlightDisplayKey key) {
    QuickFixAction.registerQuickFixAction(myInfo, fixRange, action, key);
  }

  @Override
  public void unregister(@NotNull Condition<IntentionAction> condition) {
    if (myInfo != null) {
      myInfo.unregisterQuickFix(condition);
    }
  }
}
