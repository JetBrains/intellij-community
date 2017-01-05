/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.JDOMUtil;

import java.util.Collections;

public class ShowUpdateInfoDialogAction extends AnAction {
  private boolean myShowBigData = false;

  public ShowUpdateInfoDialogAction() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    try {
      myShowBigData = ! myShowBigData;
      UpdateChannel channel = new UpdateChannel(JDOMUtil.loadDocument(getXML(myShowBigData)).getRootElement());
      BuildInfo newBuild = channel.getBuilds().get(0);
      PatchInfo patchInfo = new PatchInfo(
        JDOMUtil.loadDocument("<patch from=\"" + ApplicationInfo.getInstance().getBuild().asString() + "\" size=\"from 733 to 800\"/>")
          .getRootElement());
      new UpdateInfoDialog(channel, newBuild, patchInfo, true, UpdateSettings.getInstance().canUseSecureConnection(),
                           Collections.emptyList(), Collections.emptyList()).show();
    }
    catch (Exception ignored) {
      ignored.printStackTrace();
    }
  }


  private static final String PARAGRAPH = "    <li><strong>Refactoring to Java 8</strong>: more powerful inspections.</li>\n" +
                                          "    <li><strong>JVM Debugger</strong>: Class-level Watches; the JVM Memory View plugin.</li>\n" +
                                          "    <li><strong>User Interface</strong>: Parameter Hints; Semantic Highlighting; flat file icons.</li>\n" +
                                          "    <li><strong>Build Tools</strong>: Delegate IDE build/run actions to Gradle; Composite <br>\n" +
                                          "        Builds support; Polyglot Maven.</li>\n" +
                                          "    <li><strong>Scala</strong>: scala.meta and Scala.js</li>\n" +
                                          "    <li><strong>JavaScript</strong>: refactoring to ECMAScript 6; Flow-based inspections; <br>\n" +
                                          "        React Native debugger, Protractor, Stylelint, PostCSS, and more.</li>\n" +
                                          "    <li><strong>VCS</strong>: Faster and more ergonomic Log for Git/Mercurial; automatic <br>\n" +
                                          "        resolving conflicts; managing Git remotes.</li>\n" +
                                          "    <li><strong>Android</strong>: Blueprint; Constraint Layout, APK Analyzer, and better Instant Run.</li>\n" +
                                          "    <li><strong>Database</strong>: editing multiple cells at once; submit changes in bulk; <br>\n" +
                                          "        find usages of objects within the source code of other objects.</li>\n";

  private static final String CHANNEL_XML_START = "<!DOCTYPE products SYSTEM \"updates.dtd\">\n" +
                                                  "<channel id=\"IDEA_EAP\" name=\"IntelliJ IDEA EAP\" status=\"eap\"\n" +
                                                  "             url=\"https://confluence.jetbrains.com/display/IDEADEV/IDEA+2016.3+EAP\"\n" +
                                                  "             feedback=\"http://youtrack.jetbrains.net\"\n" +
                                                  "             majorVersion=\"2016\" licensing=\"eap\">\n" +
                                                  "      <build number=\"163.7743.44\" version=\"2016.3\" releaseDate=\"20161122\">\n" +
                                                  "        <message><![CDATA[\n" +
                                                  "        <p>Please meet IntelliJ IDEA 2016.3, the third update planned for 2016!</p>\n" +
                                                  "\n" +
                                                  "<p>Visit <a href=\"https://www.jetbrains.com/idea/whatsnew/?landing\">What's New</a> page for a full list of new features and an overview video.</p>\n" +
                                                  "\n" +
                                                  "<p>And here're the highlights:</p>\n" +
                                                  "\n" +
                                                  "<ul>\n";

  private static final String CHANNEL_XML_END = "</ul>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"Download\" url=\"https://www.jetbrains.com/idea/download/\" download=\"true\"/>\n" +
                                                "        <button name=\"What's New\" url=\"https://www.jetbrains.com/idea/whatsnew/?landing\"/>\n" +
                                                "        <patch from=\"163.7743.17\" size=\"from 2 to 18\"/>\n" +
                                                "        <patch from=\"163.7743.37\" size=\"from 2 to 18\"/>\n" +
                                                "      </build>\n" +
                                                "      <build number=\"162.2228.15\" version=\"2016.2.5\" releaseDate=\"20160712\">\n" +
                                                "        <message><![CDATA[\n" +
                                                "        <p>The <strong>IntelliJ IDEA 2016.2.5</strong> update is available. <br>\n" +
                                                "    Apart from bugfixes, the update brings support for <strong>macOS Sierra</strong>.</p>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"Download\" url=\"https://www.jetbrains.com/idea/download/\" download=\"true\"/>\n" +
                                                "        <button name=\"Release Notes\" url=\"https://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+2016.2.5+Release+Notes\"/>\n" +
                                                "        <patch from=\"162.2032.8\" size=\"from 1 to 13\"/>\n" +
                                                "        <patch from=\"162.2228.14\" size=\"from 21 to 53\"/>\n" +
                                                "        <patch from=\"162.1812.17\" size=\"from 21 to 55\"/>\n" +
                                                "        <patch from=\"162.1628.40\" size=\"from 21 to 59\"/>\n" +
                                                "        <patch from=\"162.1447.26\" size=\"from 21 to 67\"/>\n" +
                                                "        <patch from=\"162.1121.32\" size=\"from 33 to 80\"/>\n" +
                                                "      </build>\n" +
                                                "      <build number=\"162.1121.32\" version=\"2016.2\" releaseDate=\"20160712\">\n" +
                                                "        <message><![CDATA[\n" +
                                                "        <p>Welcome <strong>IntelliJ IDEA 2016.2</strong>, a second update planned for this year.<br>\n" +
                                                "    The update brings lots of new features and improvements across the <br>\n" +
                                                "    built-in tools, UI, and support for languages and frameworks.\n" +
                                                "</p>\n" +
                                                "\n" +
                                                "<p>Learn more about the update by reading the <a href=\"http://blog.jetbrains.com/idea/2016/07/intellij-idea-2016-2-is-here/\">blog post</a>.</p>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"What's New\" url=\"https://www.jetbrains.com/idea/whatsnew/\"/>\n" +
                                                "        <button name=\"Download\" url=\"https://www.jetbrains.com/idea/download/\" download=\"true\"/>\n" +
                                                "        <patch from=\"162.1121.10\" size=\"from 1 to 12\"/>\n" +
                                                "      </build>\n" +
                                                "      <build number=\"145.2070\" version=\"2016.1.4\" releaseDate=\"20160316\">\n" +
                                                "        <message><![CDATA[\n" +
                                                "        <p>IntelliJ IDEA 2016.1.4 build 145.2070 is available with important bugfixes.</p>\n" +
                                                "]]></message>\n" +
                                                "        <button name=\"Release Notes\" url=\"https://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+2016.1.4+Release+Notes\"/>\n" +
                                                "        <button name=\"Download\" url=\"https://confluence.jetbrains.com/display/IntelliJIDEA/Previous+IntelliJ+IDEA+Releases\"\n" +
                                                "                download=\"true\"/>\n" +
                                                "        <patch from=\"145.1617\" size=\"from 42 to 58\"/>\n" +
                                                "      </build>\n" +
                                                "    </channel>\n";
  private static String getXML(boolean bigData) {
    StringBuilder sb = new StringBuilder(CHANNEL_XML_START);
    int count = bigData ? 4 : 1;
    for (int i = 0; i < count; i++) { sb.append(PARAGRAPH); }
    sb.append(CHANNEL_XML_END);
    return sb.toString();
  }
}
