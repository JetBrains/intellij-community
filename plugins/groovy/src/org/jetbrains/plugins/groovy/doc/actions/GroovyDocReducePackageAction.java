package org.jetbrains.plugins.groovy.doc.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.plugins.grails.GrailsBundle;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.10.2008
 */
public class GroovyDocReducePackageAction extends AnAction {
  private final JList myPackagesList;
  private final DefaultListModel myDataModel;

  public GroovyDocReducePackageAction(final JList packagesList, final DefaultListModel dataModel) {
    super(GrailsBundle.message("groovydoc.remove.package"), GrailsBundle.message("groovydoc.remove.package"),
          IconLoader.getIcon("/general/remove.png"));
    myPackagesList = packagesList;
    myDataModel = dataModel;
  }

  public void actionPerformed(final AnActionEvent e) {
    myDataModel.remove(myPackagesList.getSelectedIndex());
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    if (myPackagesList.getSelectedIndex() == -1) {
      presentation.setEnabled(false);
    } else {
      presentation.setEnabled(true);
    }
    super.update(e);
  }
}