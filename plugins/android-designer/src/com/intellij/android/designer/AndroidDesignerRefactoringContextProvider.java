package com.intellij.android.designer;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.EmptyXmlTag;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.refactoring.AndroidRefactoringContextProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDesignerRefactoringContextProvider implements AndroidRefactoringContextProvider {
  @NotNull
  @Override
  public XmlTag[] getComponentTags(@NotNull DataContext dataContext) {
    final EditableArea area = EditableArea.DATA_KEY.getData(dataContext);

    if (area == null) {
      return XmlTag.EMPTY;
    }
    final List<RadComponent> selection = area.getSelection();

    if (selection.size() == 0) {
      return XmlTag.EMPTY;
    }
    final List<XmlTag> tags = new ArrayList<XmlTag>(selection.size());

    for (RadComponent component : selection) {
      if (!(component instanceof RadViewComponent)) {
        return XmlTag.EMPTY;
      }
      final XmlTag tag = ((RadViewComponent)component).getTag();

      if (tag == null || tag.equals(EmptyXmlTag.INSTANCE)) {
        return XmlTag.EMPTY;
      }
      tags.add(tag);
    }
    return tags.toArray(new XmlTag[tags.size()]);
  }
}
