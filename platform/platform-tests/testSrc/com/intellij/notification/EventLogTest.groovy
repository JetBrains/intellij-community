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
package com.intellij.notification;


import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestCase

/**
 * @author peter
 */
class EventLogTest extends LightPlatformTestCase {

  EventLogTest() {
    PlatformTestCase.initPlatformLangPrefix()
  }

  public void testHtmlEntities() {
    def entry = EventLog.formatForLog(new Notification("xxx", "Title", "Hello&nbsp;world&laquo;&raquo;", NotificationType.ERROR))
    assert entry.message == 'Title: Hello world<<>>'
  }

  public void testParseMultilineText() {
    def entry = EventLog.formatForLog(new Notification("xxx", "Title", "<html><body> " +
                                                                       "<font size=\"3\">first line<br>" +
                                                                       "second line<br>" +
                                                                       "third<br>" +
                                                                       "<a href=\"create\">Action</a><br>" +
                                                                       "</body></html>", NotificationType.ERROR))
    assert entry.message == 'Title:  first line second line third // Action (show balloon)'
    //                       0                                       40      48          60
    assert entry.links.collect { it.first } == [new TextRange(40, 46), new TextRange(48, 60)]

  }

  public void testInParagraph() {
    def entry = EventLog.formatForLog(new Notification("xxx", "Title", "<p>message</p>", NotificationType.ERROR))
    assert entry.message == 'Title: message'
  }

  public void testJavaSeparators() {
    def entry = EventLog.formatForLog(new Notification("xxx", "Title", "fst\nsnd", NotificationType.ERROR))
    assert entry.message == 'Title: fst snd'
  }

  public void testLinkInTitle() {
    def entry = EventLog.formatForLog(new Notification("xxx", '<a href="a">link</a>', "content", NotificationType.ERROR))
    assert entry.message == 'link: content'
    assert entry.links.collect { it.first } == [new TextRange(0, 4)]
  }

  public void testMalformedLink() throws Exception {
    def entry = EventLog.formatForLog(new Notification("xxx", '<a href="a">link<a/>', "content", NotificationType.ERROR))
    assert entry.message ==  'link: content (show balloon)'
  }

  public void testVariousNewlines() throws Exception {
    assert EventLog.formatForLog(new Notification("xxx", 'title', "foo<br/>bar", NotificationType.ERROR)).message ==  'title: foo bar'
    assert EventLog.formatForLog(new Notification("xxx", 'title', "foo<br/>/bar", NotificationType.ERROR)).message ==  'title: foo // /bar'
    assert EventLog.formatForLog(new Notification("xxx", 'title', "foo<br/>Bar", NotificationType.ERROR)).message ==  'title: foo // Bar'
  }

  public void testManyNewlines() throws Exception {
    assert EventLog.formatForLog(new Notification("xxx", 'title', "foo\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\nbar", NotificationType.ERROR)).message ==  'title: foo bar'
  }

}
