// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.lang.documentation.ide.ui.parseExpandableDefinition
import junit.framework.TestCase
import kotlin.test.assertNotEquals

class ExpandableDefinitionTest: TestCase() {
  fun testShort() {
    val short = "<div class='content-only'><div class='definition expandable'><pre><span style=\"color:#cf8e6d;\">interface&#32;</span><span style=\"\">Box</span> <span style=\"\">{<br></span><span style=\"\">&#32;&#32;&#32;&#32;scale:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;x:&#32;</span><span style=\"color:#cf8e6d;\">number<br></span><span style=\"\">}</span></pre></div><table class='sections'><tr><td valign='top'><icon src='JavaScriptPsiIcons.FileTypes.TypeScriptFile'/>&nbsp;src/foo/test.ts</td></table></div><div class=\"bottom\"><icon src=\"0\"/>&nbsp;TestProject</div>"
    val definition = parseExpandableDefinition(short, 4)!!
    assertEquals(
      "<div class='content-only'><div class='definition'><pre><span style=\"color:#cf8e6d;\">interface&#32;</span><span style=\"\">Box</span> <span style=\"\">{<br></span><span style=\"\">&#32;&#32;&#32;&#32;scale:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;x:&#32;</span><span style=\"color:#cf8e6d;\">number<br></span><span style=\"\">}</span></pre></div><table class='sections'><tr><td valign='top'><icon src='JavaScriptPsiIcons.FileTypes.TypeScriptFile'/>&nbsp;src/foo/test.ts</td></table></div><div class=\"bottom\"><icon src=\"0\"/>&nbsp;TestProject</div>",
      definition.getDecorated())
  }

  fun testCollapsedExpanded() {
    val definition = parseExpandableDefinition(
      "<div class='content-only'><div class='definition expandable'><pre><span style=\"color:#cf8e6d;\">interface&#32;</span><span style=\"\">Box</span> <span style=\"\">{<br></span><span style=\"\">&#32;&#32;&#32;&#32;scale:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;x:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;y:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;z:&#32;</span><span style=\"color:#cf8e6d;\">number<br></span><span style=\"\">}</span></pre></div><table class='sections'><tr><td valign='top'><icon src='JavaScriptPsiIcons.FileTypes.TypeScriptFile'/>&nbsp;src/foo/test.ts</td></table></div><div class=\"bottom\"><icon src=\"0\"/>&nbsp;TestProject</div>",
      4)!!
    assertNotNull(definition)
    val collapsed = "<div class='content-only'><div class='definition'><pre><span style=\"color:#cf8e6d;\">interface&#32;</span><span style=\"\">Box</span> <span style=\"\">{<br></span><span style=\"\">&#32;&#32;&#32;&#32;scale:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;x:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;y:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br><a href=\"toggle.expandable.definition\">Show more</a></pre></div><table class='sections'><tr><td valign='top'><icon src='JavaScriptPsiIcons.FileTypes.TypeScriptFile'/>&nbsp;src/foo/test.ts</td></table></div><div class=\"bottom\"><icon src=\"0\"/>&nbsp;TestProject</div>"
    assertEquals(collapsed, definition.getDecorated())
    definition.toggleExpanded()
    assertNotEquals(collapsed, definition.getDecorated())
    val expanded = "<div class='content-only'><div class='definition'><pre><span style=\"color:#cf8e6d;\">interface&#32;</span><span style=\"\">Box</span> <span style=\"\">{<br></span><span style=\"\">&#32;&#32;&#32;&#32;scale:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;x:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;y:&#32;</span><span style=\"color:#cf8e6d;\">number</span><span style=\"\">,<br></span><span style=\"\">&#32;&#32;&#32;&#32;z:&#32;</span><span style=\"color:#cf8e6d;\">number<br></span><span style=\"\">}</span><br/><a href=\"toggle.expandable.definition\">Show less</a></pre></div><table class='sections'><tr><td valign='top'><icon src='JavaScriptPsiIcons.FileTypes.TypeScriptFile'/>&nbsp;src/foo/test.ts</td></table></div><div class=\"bottom\"><icon src=\"0\"/>&nbsp;TestProject</div>"
    assertEquals(expanded, definition.getDecorated())
  }
}