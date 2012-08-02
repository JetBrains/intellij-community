package com.intellij.android.designer;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.EmptyXmlTag;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.refactoring.AndroidRefactoringContextProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDesignerRefactoringContextProvider implements AndroidRefactoringContextProvider {
  @Override
  public XmlTag getComponentTag(@NotNull DataContext dataContext) {
    final EditableArea area = EditableArea.DATA_KEY.getData(dataContext);

    if (area == null) {
      return null;
    }
    final List<RadComponent> selection = area.getSelection();

    if (selection.size() != 1) {
      return null;
    }
    final RadComponent component = selection.get(0);

    if (!(component instanceof RadViewComponent)) {
      return null;
    }
    final XmlTag tag = ((RadViewComponent)component).getTag();
    return tag != null && !tag.equals(EmptyXmlTag.INSTANCE) ? tag : null;
  }
}
