/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures

import com.intellij.ui.HyperlinkLabel
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import java.awt.Point
import java.awt.Rectangle


class HyperlinkLabelFixture(robot: Robot, val hyperlinkLabel: HyperlinkLabel) : ComponentFixture<HyperlinkLabelFixture, HyperlinkLabel>(
  HyperlinkLabelFixture::class.java, robot,
  hyperlinkLabel) {

  fun clickLink(regionText: String) {
    if (!hyperlinkLabel.hightlightedRegionsBoundsMap.containsKey(regionText)) throw ComponentLookupException(
      "Unable to find highlighted region \"$regionText\" in HyperlinkLabel:\"${hyperlinkLabel.text}\"")
    val rectangle = hyperlinkLabel.hightlightedRegionsBoundsMap[regionText]
    driver().click(hyperlinkLabel, rectangle!!.center())
  }

  private fun Rectangle.center(): Point
    = Point(this.x + this.width / 2, this.y + this.height / 2)

}