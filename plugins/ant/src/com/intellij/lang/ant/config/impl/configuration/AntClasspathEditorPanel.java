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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.config.impl.AllJarsUnderDirEntry;
import com.intellij.lang.ant.config.impl.AntClasspathEntry;
import com.intellij.lang.ant.config.impl.SinglePathEntry;
import com.intellij.ui.ListUtil;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ListProperty;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

public class AntClasspathEditorPanel extends JPanel {
  private ListProperty<AntClasspathEntry> myClasspathProperty;
  private final Form myForm = new Form();
  private UIPropertyBinding.Composite myBinding;

  public AntClasspathEditorPanel() {
    super(new BorderLayout());
    add(myForm.myWholePanel, BorderLayout.CENTER);
  }

  public UIPropertyBinding setClasspathProperty(ListProperty<AntClasspathEntry> classpathProperty) {
    myClasspathProperty = classpathProperty;
    myBinding = new UIPropertyBinding.Composite();
    UIPropertyBinding.OrderListBinding<AntClasspathEntry> classpathBinding = myBinding.bindList(myForm.myClasspathList, myClasspathProperty);
    classpathBinding.addAddManyFacility(myForm.myAddButton,
                                        new SinglePathEntry.AddEntriesFactory(myForm.myClasspathList));
    classpathBinding.addAddManyFacility(myForm.myAddAllInDir,
                                        new AllJarsUnderDirEntry.AddEntriesFactory(myForm.myClasspathList));
    myBinding.addBinding(new UIPropertyBinding() {
      @Override
      public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      }

      @Override
      public void apply(AbstractProperty.AbstractPropertyContainer container) {
      }

      @Override
      public void beDisabled() {
        myForm.enableButtons(false);
      }

      @Override
      public void beEnabled() {
        myForm.enableButtons(true);
      }

      @Override
      public void addAllPropertiesTo(Collection<? super AbstractProperty> properties) {
      }
    });
    return myBinding;
  }

  public static class Form {
    private JButton myAddButton;
    private JButton myAddAllInDir;
    private JButton myRemoveButton;
    private JButton myMoveUpButton;
    private JButton myMoveDownButton;
    private JPanel myWholePanel;
    private JList myClasspathList;
    private final ArrayList<ListUtil.Updatable> myUpdatables = new ArrayList<>();

    public Form() {
      myClasspathList.setCellRenderer(new AntUIUtil.ClasspathRenderer());

      myUpdatables.add(ListUtil.addRemoveListener(myRemoveButton, myClasspathList));
      myUpdatables.add(ListUtil.addMoveUpListener(myMoveUpButton, myClasspathList));
      myUpdatables.add(ListUtil.addMoveDownListener(myMoveDownButton, myClasspathList));
    }

    public void enableButtons(boolean enable) {
      for (ListUtil.Updatable updatable : myUpdatables) {
        updatable.enable(enable);
      }
    }
  }

  public JComponent getPreferedFocusComponent() {
    return myForm.myClasspathList;
  }
}
