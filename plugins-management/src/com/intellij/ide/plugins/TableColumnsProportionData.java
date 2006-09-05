/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 05-Sep-2006
 * Time: 20:18:57
 */
package com.intellij.ide.plugins;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

public class TableColumnsProportionData implements JDOMExternalizable {
  public String myProportion;

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public void restoreProportion(JTable table) {
    if (myProportion != null) {
      final TableColumnModel model = table.getColumnModel();
      final String[] widths = myProportion.split(":");
      for(int i = 0; i < widths.length; i++){
        final TableColumn column = model.getColumn(i);
        final int width = Integer.parseInt(widths[i]);
        column.setWidth(width);
        column.setPreferredWidth(width);
      }
    }
  }


  public void saveProportion(JTable table){
    myProportion = "";
    final TableColumnModel model = table.getColumnModel();
    for(int i = 0; i < model.getColumnCount(); i++) {
      myProportion += ":" + model.getColumn(i).getWidth();
    }
    if (myProportion.length() > 0) {
      myProportion = myProportion.substring(1);
    }
  }
}