// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
    consumer.consume(ArchiveFileType.INSTANCE, "zip;jar;war;ear;swc;ane;egg;apk");
    consumer.consume(PlainTextFileType.INSTANCE, "txt;sh;bat;cmd;policy;log;cgi;MF;jad;jam;htaccess;rb;opts");
    consumer.consume(NativeFileType.INSTANCE, "doc;docx;xls;xlsx;ppt;pptx;mdb;vsd;pdf;hlp;chm;odt");
    consumer.consume(UnknownFileType.INSTANCE, "lib;dll;a;so;dylib");
  }
}
