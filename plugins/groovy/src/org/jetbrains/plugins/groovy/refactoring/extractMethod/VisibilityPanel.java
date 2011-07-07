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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.psi.PsiModifier;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.EventListener;

/**
 * @author ilyas
 */
public class VisibilityPanel extends JPanel {
  private final JRadioButton myRbPrivate;
  private final JRadioButton myRbProtected;
  private final JRadioButton myRbPublic;

  public VisibilityPanel() {
    setBorder(IdeBorderFactory.createTitledBorder(GroovyRefactoringBundle.message("visibility.border.title")));
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    ButtonGroup bg = new ButtonGroup();

    ItemListener listener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if(e.getStateChange() == ItemEvent.SELECTED) {
          fireStateChanged();
        }
      }
    };

    myRbPrivate = new JRadioButton();
    myRbPrivate.setText(GroovyRefactoringBundle.message("visibility.private"));
    myRbPrivate.addItemListener(listener);
    myRbPrivate.setFocusable(false);
    myRbPrivate.setMnemonic(KeyEvent.VK_V);
    add(myRbPrivate);
    bg.add(myRbPrivate);


    myRbProtected = new JRadioButton();
    myRbProtected.setText(GroovyRefactoringBundle.message("visibility.protected"));
    myRbProtected.addItemListener(listener);
    myRbProtected.setFocusable(false);
    myRbProtected.setMnemonic(KeyEvent.VK_O);
    add(myRbProtected);
    bg.add(myRbProtected);

    myRbPublic = new JRadioButton();
    myRbPublic.setText(GroovyRefactoringBundle.message("visibility.public"));
    myRbPublic.addItemListener(listener);
    myRbPublic.setFocusable(false);
    myRbPublic.setMnemonic(KeyEvent.VK_B);
    add(myRbPublic);
    bg.add(myRbPublic);
  }


  @NotNull
  public String getVisibility() {
    if (myRbPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myRbProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    if (myRbPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    }
    return PsiModifier.PRIVATE;
  }

  public void setVisibilityEnabled(String visibility, boolean value) {
    if(PsiModifier.PRIVATE.equals(visibility)) myRbPrivate.setEnabled(value);
    else if(PsiModifier.PROTECTED.equals(visibility)) myRbProtected.setEnabled(value);
    else if(PsiModifier.PUBLIC.equals(visibility)) myRbPublic.setEnabled(value);
  }

  public void setVisibility(String visibility) {
    if (PsiModifier.PUBLIC.equals(visibility)) {
      myRbPublic.setSelected(true);
    }
    else if (PsiModifier.PROTECTED.equals(visibility)) {
      myRbProtected.setSelected(true);
    }
    else if (PsiModifier.PRIVATE.equals(visibility)) {
      myRbPrivate.setSelected(true);
    }
  }

  public static interface VisibilityStateChanged extends EventListener {
    void visibilityChanged(String newVisibility);
  }

  public void addStateChangedListener(VisibilityStateChanged l) {
    listenerList.add(VisibilityStateChanged.class, l);
  }

  public void fireStateChanged() {
    Object[] list = listenerList.getListenerList();

    String visibility = getVisibility();
    for (Object obj : list) {
      if (obj instanceof VisibilityStateChanged) {
        ((VisibilityStateChanged)obj).visibilityChanged(visibility);
      }
    }
  }
}
