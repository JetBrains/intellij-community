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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 09/16/2010
 */
public class SoftWrapApplianceManagerTest extends LightPlatformCodeInsightTestCase {

  private static final String PATH = "/codeInsight/softwrap/";

  @Override
  protected void tearDown() throws Exception {
    myEditor.getSettings().setUseSoftWraps(false);
    super.tearDown();
  }

  public void testSoftWrapAdditionOnTyping() throws Exception {
    init(800);

    int offset = myEditor.getDocument().getTextLength() + 1;
    assertNull(myEditor.getSoftWrapModel().getSoftWrap(offset));
    type(" thisisalongtokenthatisnotexpectedtobebrokenintopartsduringsoftwrapping");
    assertNotNull(myEditor.getSoftWrapModel().getSoftWrap(offset));
  }

  private void init(final int visibleWidth) throws Exception {
    configureByFile(PATH + getTestName(false) + ".txt");
    myEditor.getSettings().setUseSoftWraps(true);
    SoftWrapModelImpl model = (SoftWrapModelImpl)myEditor.getSoftWrapModel();
    model.refreshSettings();

    SoftWrapApplianceManager applianceManager = model.getApplianceManager();
    applianceManager.setWidthProvider(new SoftWrapApplianceManager.VisibleAreaWidthProvider() {
      @Override
      public int getVisibleAreaWidth() {
        return visibleWidth;
      }
    });
    applianceManager.registerSoftWrapIfNecessary(new Rectangle(visibleWidth, visibleWidth * 2), 0);
  }
}
