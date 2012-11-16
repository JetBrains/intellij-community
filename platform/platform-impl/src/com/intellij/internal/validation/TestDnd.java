/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal.validation;

import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.DnDActionInfo;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDImage;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class TestDnd extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new DialogWrapper(getEventProject(e)) {
      {
        setTitle("DnD Test");
        setSize(600, 500);
        init();
      }
      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        JBList list = new JBList(new String[]{"1111111", "222222", "333333", "44444", "555555555555555555555555"});
        DnDSupport.createBuilder(list)
          .setBeanProvider(new Function<DnDActionInfo, DnDDragStartBean>() {
            @Override
            public DnDDragStartBean fun(DnDActionInfo info) {
              return new DnDDragStartBean("something");
            }
          })
          .setImageProvider(new Function<DnDActionInfo, DnDImage>() {
            @Override
            public DnDImage fun(DnDActionInfo info) {
              return new DnDImage(IconUtil.toImage(AllIcons.Icon));
            }
          })
          .install();

        return list;
      }
    }.show();
  }
}