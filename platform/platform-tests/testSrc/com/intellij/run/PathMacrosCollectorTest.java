// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.run;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.testFramework.UsefulTestCase;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public class PathMacrosCollectorTest extends UsefulTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    MockApplication.setUp(getTestRootDisposable());
  }

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
    root.addContent(new Text("file://$root$/some/path/just$file$name.txt"));

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, null, new PathMacrosImpl(false));
    UsefulTestCase.assertSameElements(macros, "MACro1", "macro4", "mac_ro6", "macr.o7", "mac-ro8", "root");
  }

  public void testWithRecursiveFilter() {
    Element root = new Element("root");
    final Element configuration = new Element("configuration");
    configuration.setAttribute("value", "some text$macro5$fdsjfhdskjfsd$MACRO$");
    configuration.setAttribute("value2", "file://$root$/some/path/just$file$name.txt");
    root.addContent(configuration);

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, new PathMacroFilter() {
                                                                    @Override
                                                                    public boolean recursePathMacros(@NotNull Attribute attribute) {
                                                                      return "value".equals(attribute.getName());
                                                                    }
                                                                  }, new PathMacrosImpl(false));
    UsefulTestCase.assertSameElements(macros, "macro5", "MACRO", "root");
  }

  public void testWithFilter() {
    Element root = new Element("root");
    final Element testTag = new Element("test");
    testTag.setAttribute("path", "$MACRO$");
    testTag.setAttribute("ignore", "$PATH$");
    root.addContent(testTag);

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, null, new PathMacrosImpl(false));
    assertEquals(2, macros.size());
    assertTrue(macros.contains("MACRO"));
    assertTrue(macros.contains("PATH"));

    final Set<String> filtered = PathMacrosCollector.getMacroNames(root, new PathMacroFilter() {
                                                                     @Override
                                                                     public boolean skipPathMacros(@NotNull Attribute attribute) {
                                                                       return "ignore".equals(attribute.getName());
                                                                     }
                                                                   }, new PathMacrosImpl(false));

    assertEquals(1, filtered.size());
    assertTrue(macros.contains("MACRO"));
  }
}
