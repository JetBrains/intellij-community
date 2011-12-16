/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.run;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.util.NotNullFunction;
import junit.framework.TestCase;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 14, 2006
 */
public class PathMacrosCollectorTest extends TestCase {
  public void testCollectMacros() {
    Element root = new Element("root");
    root.addContent(new Text("$MACro1$ some text $macro2$ other text $MACRo3$"));
    root.addContent(new Text("$macro4$ some text"));
    root.addContent(new Text("some text$macro5$"));
    root.addContent(new Text("file:$mac_ro6$"));
    root.addContent(new Text("jar://$macr.o7$ "));
    root.addContent(new Text("$mac-ro8$ "));
    root.addContent(new Text("$$$ "));
    root.addContent(new Text("$c:\\a\\b\\c$ "));
    root.addContent(new Text("$Revision 1.23$"));

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, null, null, new PathMacrosImpl());

    assertEquals(5, macros.size());
    assertTrue(macros.contains("MACro1"));
    assertTrue(macros.contains("macro4"));
    assertTrue(macros.contains("mac_ro6"));
    assertTrue(macros.contains("macr.o7"));
    assertTrue(macros.contains("mac-ro8"));
  }

  public void testWithRecursiveFilter() throws Exception {
    Element root = new Element("root");
    final Element configuration = new Element("configuration");
    configuration.setAttribute("value", "some text$macro5$fdsjfhdskjfsd$MACRO$");
    root.addContent(configuration);

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, null, new NotNullFunction<Object, Boolean>() {
      @Override
      @NotNull
      public Boolean fun(Object o) {
        return (o instanceof Attribute) && "value".equals(((Attribute)o).getName());
      }
    }, new PathMacrosImpl());

    assertEquals(2, macros.size());
    assertTrue(macros.contains("macro5"));
    assertTrue(macros.contains("MACRO"));
  }

  public void testWithFilter() throws Exception {
    Element root = new Element("root");
    final Element testTag = new Element("test");
    testTag.setAttribute("path", "$MACRO$");
    testTag.setAttribute("ignore", "$PATH$");
    root.addContent(testTag);

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, null, null, new PathMacrosImpl());
    assertEquals(2, macros.size());
    assertTrue(macros.contains("MACRO"));
    assertTrue(macros.contains("PATH"));

    final Set<String> filtered = PathMacrosCollector.getMacroNames(root, new NotNullFunction<Object, Boolean>() {
      @Override
      @NotNull
      public Boolean fun(Object o) {
        if (o instanceof Attribute && "ignore".equals(((Attribute)o).getName())) return false;
        return true;
      }
    }, null, new PathMacrosImpl());

    assertEquals(1, filtered.size());
    assertTrue(macros.contains("MACRO"));
  }
}
