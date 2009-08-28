package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(final @NotNull FileTypeConsumer consumer) {
    consumer.consume(new ArchiveFileType(), "zip;jar;war;ear;swc;egg"); // egg for python
    consumer.consume(new PlainTextFileType(),
                     new ExtensionFileNameMatcher("txt"),
                     new ExtensionFileNameMatcher("sh"),
                     new ExtensionFileNameMatcher("bat"),
                     new ExtensionFileNameMatcher("cmd"),
                     new ExtensionFileNameMatcher("policy"),
                     new ExtensionFileNameMatcher("log"),
                     new ExtensionFileNameMatcher("cgi"),
                     new ExtensionFileNameMatcher("MF"),
                     new ExtensionFileNameMatcher("sql"),
                     new ExtensionFileNameMatcher("jad"),
                     new ExtensionFileNameMatcher("jam"),
                     new ExtensionFileNameMatcher("htaccess"),
                     new ExactFileNameMatcher("readme", true));
    consumer.consume(NativeFileType.INSTANCE, "doc;xls;ppt;mdb;vsd;pdf;hlp;chm;odt");
    consumer.consume(UnknownFileType.INSTANCE);
  }
}
