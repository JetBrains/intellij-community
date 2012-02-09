/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author Denis Zhdanov
 * @since 4/19/11 6:14 PM
 */
public class KillRegionActionTest extends AbstractRegionToKillRingTest {

  protected void doTest(@NotNull String text) throws Exception {
    configureFromFileText(getTestName(false) + ".txt", text);
    Pair<String,String> parseResult = parse();
    killRegion();
    if (parseResult.first != null) {
      Transferable contents = CopyPasteManager.getInstance().getContents();
      assertNotNull(contents);
      assertEquals(parseResult.first, contents.getTransferData(DataFlavor.stringFlavor));
    }
    
    assertEquals(parseResult.second, myEditor.getDocument().getText());
  }
}
