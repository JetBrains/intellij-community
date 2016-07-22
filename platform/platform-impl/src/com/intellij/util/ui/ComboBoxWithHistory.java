package com.intellij.util.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"UnusedDeclaration"})
public class ComboBoxWithHistory extends JComboBox {
  private final String myHistoryId;
  private Project myProject;
  private final HashMap<Object, Long> myWeights = new HashMap<>();
  private boolean myAutoSave = true;

  public ComboBoxWithHistory(@Nullable Project project, String historyId, Object[] items) {
    super();
    myHistoryId = historyId;
    myProject = project;
    setModelFrom(items);
  }

  public ComboBoxWithHistory(@NotNull String historyId, Object[] items) {
    this(null, historyId, items);
  }

  public ComboBoxWithHistory(String historyId) {
    this(null, historyId, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public void setModelFrom(Object... items) {
    setModel(new MyModel(items));
  }

  public boolean isAutoSave() {
    return myAutoSave;
  }

  public void setAutoSave(boolean autoSave) {
    myAutoSave = autoSave;
  }

  public void save() {
    final StringBuilder buf = new StringBuilder("<map>");
    for (Object key : myWeights.keySet()) {
      if (key != null) {
        final Long value = myWeights.get(key);
        if (value != null) {
          buf.append("<element>")
             .append("<key>").append(key).append("</key>")
             .append("<value>").append(value).append("</value>")
             .append("</element>");
        }
      }
    }

    final String xml = buf.append("</map>").toString();

    if (myProject == null) {
      PropertiesComponent.getInstance().setValue(myHistoryId, xml);
    } else {
      PropertiesComponent.getInstance(myProject).setValue(myHistoryId, xml);
    }
  }

  public void load() {
    final String xml = myProject == null ? PropertiesComponent.getInstance().getValue(myHistoryId)
                                   : PropertiesComponent.getInstance(myProject).getValue(myHistoryId);
    myWeights.clear();

    if (xml == null) return;

    try {
      final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      final DocumentBuilder db = dbf.newDocumentBuilder();
      final InputSource is = new InputSource();
      is.setCharacterStream(new StringReader(xml));

      Document doc = db.parse(is);
      NodeList nodes = doc.getElementsByTagName("map");
      if (nodes.getLength() == 1) {
        final NodeList map = nodes.item(0).getChildNodes();
        for (int i = 0; i < map.getLength(); i++) {
          final Node item = map.item(i);
          final NodeList list = item.getChildNodes();
          final Element key = (Element)list.item(0);
          final Element value = (Element)list.item(1);
          myWeights.put(key.getTextContent(), Long.valueOf(value.getTextContent()));
        }
      }
    }
    catch (Exception e) {//
    }
  }

  public void setProject(Project project) {
    myProject = project;
    load();
  }

  private static Object[] sort(final Object[] items, HashMap<Object, Long> weights) {
    Arrays.sort(items, new LastUsedComparator(weights, Arrays.asList(items)));
    return items;
  }


  private class MyModel extends DefaultComboBoxModel {
    private MyModel(Object[] items) {
      super(sort(items, myWeights));
    }

    @Override
    public void setSelectedItem(Object o) {
      super.setSelectedItem(o);
      if (o != null && isAutoSave()) {
        myWeights.put(o.toString(), System.currentTimeMillis());
        save();
      }
    }
  }

  private static class LastUsedComparator implements Comparator<Object> {
    private final HashMap<Object, Long> myWeights;
    private final List<Object> myInitialPositions;

    private LastUsedComparator(HashMap<Object, Long> weights, List<Object> initialPositions) {
      myWeights = weights;
      myInitialPositions = initialPositions;
    }

    @Override
    public int compare(Object o1, Object o2) {
      final Long w1 = myWeights.get(o1);
      final Long w2 = myWeights.get(o2);
      if (w1 != null || w2 != null) {
        return w1 != null && w2 != null ? sign(w2 - w1) : w1 != null ? -1 : 1;
      }
      return myInitialPositions.indexOf(o1) - myInitialPositions.indexOf(o2);
    }

    private static int sign(Long l) {
      return l == 0 ? 0 : l < 0 ? -1 : 1;
    }
  }
}
