/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.logcat;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 3, 2009
 * Time: 6:54:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidLogcatColorPage implements ColorSettingsPage {
  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS = new HashMap<String, TextAttributesKey>();
  private static final String DEMO_TEXT = "Logcat:\n" +
                                          "<verbose>02-02 18:52:57.132: VERBOSE/ProtocolEngine(24): DownloadRate 104166 bytes per sec. Downloaded Bytes 5643/34714</verbose>\n" +
                                          "<debug>08-03 13:31:16.196: DEBUG/dalvikvm(2227): HeapWorker thread shutting down</debug>\n" +
                                          "<info>08-03 13:31:16.756: INFO/dalvikvm(2234): Debugger is active</info>\n" +
                                          "<warning>08-03 16:26:45.965: WARN/ActivityManager(564): Launch timeout has expired, giving up wake lock!</warning>\n" +
                                          "<error>08-04 16:19:11.166: ERROR/AndroidRuntime(4687): Uncaught handler: thread main exiting due to uncaught exception</error>\n" +
                                          "<assert>08-04 16:24:11.166: ASSERT/Assertion(4687): Expected true but was false</assert>";

  static {
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("verbose", AndroidLogcatConstants.VERBOSE_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("debug", AndroidLogcatConstants.DEBUG_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("info", AndroidLogcatConstants.INFO_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("warning", AndroidLogcatConstants.WARNING_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("error", AndroidLogcatConstants.ERROR_OUTPUT_KEY);
    ADDITIONAL_HIGHLIGHT_DESCRIPTORS.put("assert", AndroidLogcatConstants.ASSERT_OUTPUT_KEY);
  }

  private static final AttributesDescriptor[] ATTRIBUTES_DESCRIPTORS =
    new AttributesDescriptor[]{new AttributesDescriptor(AndroidBundle.message("verbose.level.title"), AndroidLogcatConstants.VERBOSE_OUTPUT_KEY),
      new AttributesDescriptor(AndroidBundle.message("info.level.title"), AndroidLogcatConstants.INFO_OUTPUT_KEY),
      new AttributesDescriptor(AndroidBundle.message("debug.level.title"), AndroidLogcatConstants.DEBUG_OUTPUT_KEY),
      new AttributesDescriptor(AndroidBundle.message("warning.level.title"), AndroidLogcatConstants.WARNING_OUTPUT_KEY),
      new AttributesDescriptor(AndroidBundle.message("error.level.title"), AndroidLogcatConstants.ERROR_OUTPUT_KEY),
      new AttributesDescriptor(AndroidBundle.message("assert.level.title"), AndroidLogcatConstants.ASSERT_OUTPUT_KEY)};

  @NotNull
  public String getDisplayName() {
    return AndroidBundle.message("android.logcat.color.page.name");
  }

  public Icon getIcon() {
    return AndroidUtils.ANDROID_ICON;
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRIBUTES_DESCRIPTORS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NotNull
  public String getDemoText() {
    return DEMO_TEXT;
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }
}
