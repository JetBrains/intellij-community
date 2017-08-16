/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.run;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Text;

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
    root.addContent(new Text("file://$root$/some/path/just$file$name.txt"));

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, null, new PathMacrosImpl());
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
                                                                    public boolean recursePathMacros(Attribute attribute) {
                                                                      return "value".equals(attribute.getName());
                                                                    }
                                                                  }, new PathMacrosImpl());
    UsefulTestCase.assertSameElements(macros, "macro5", "MACRO", "root");
  }

  public void testWithFilter() {
    Element root = new Element("root");
    final Element testTag = new Element("test");
    testTag.setAttribute("path", "$MACRO$");
    testTag.setAttribute("ignore", "$PATH$");
    root.addContent(testTag);

    final Set<String> macros = PathMacrosCollector.getMacroNames(root, null, new PathMacrosImpl());
    assertEquals(2, macros.size());
    assertTrue(macros.contains("MACRO"));
    assertTrue(macros.contains("PATH"));

    final Set<String> filtered = PathMacrosCollector.getMacroNames(root, new PathMacroFilter() {
                                                                     @Override
                                                                     public boolean skipPathMacros(Attribute attribute) {
                                                                       return "ignore".equals(attribute.getName());
                                                                     }
                                                                   }, new PathMacrosImpl());

    assertEquals(1, filtered.size());
    assertTrue(macros.contains("MACRO"));
  }
}
