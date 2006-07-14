package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.lang.ant.config.impl.AllJarsUnderDirEntry;
import com.intellij.lang.ant.config.impl.AntClasspathEntry;
import com.intellij.lang.ant.config.impl.SinglePathEntry;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.ListUtil;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ListProperty;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

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
      public void loadValues(AbstractProperty.AbstractPropertyContainer container) {
      }

      public void apply(AbstractProperty.AbstractPropertyContainer container) {
      }

      public void beDisabled() {
        myForm.enableButtons(false);
      }

      public void beEnabled() {
        myForm.enableButtons(true);
      }

      public void addAllPropertiesTo(Collection<AbstractProperty> properties) {
      }
    });
    return myBinding;
  }

  private static class Form {
    private JButton myAddButton;
    private JButton myAddAllInDir;
    private JButton myRemoveButton;
    private JButton myMoveUpButton;
    private JButton myMoveDownButton;
    private JPanel myWholePanel;
    private JLabel myClasspathLabel;
    private JList myClasspathList;
    private final ArrayList<ListUtil.Updatable> myUpdatables = new ArrayList<ListUtil.Updatable>();

    public Form() {
      myClasspathLabel.setLabelFor(myClasspathList);
      myClasspathList.setCellRenderer(new AntUIUtil.ClasspathRenderer());

      myUpdatables.add(ListUtil.addRemoveListener(myRemoveButton, myClasspathList));
      myUpdatables.add(ListUtil.addMoveUpListener(myMoveUpButton, myClasspathList));
      myUpdatables.add(ListUtil.addMoveDownListener(myMoveDownButton, myClasspathList));
    }

    public void enableButtons(boolean enable) {
      for (Iterator<ListUtil.Updatable> iterator = myUpdatables.iterator(); iterator.hasNext();) {
        ListUtil.Updatable updatable = iterator.next();
        updatable.enable(enable);
      }
    }
  }

  public void setClasspathLabel(String textWithMnemonic) {
    LabeledComponent.TextWithMnemonic.fromTextWithMnemonic(textWithMnemonic).setToLabel(myForm.myClasspathLabel);
  }

  public String getClasspathLabel() {
    return LabeledComponent.TextWithMnemonic.fromLabel(myForm.myClasspathLabel).getTextWithMnemonic();
  }

  public JComponent getPreferedFocusComponent() {
    return myForm.myClasspathList;
  }
}
