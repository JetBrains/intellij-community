// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package git4idea.vfs;

import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import org.jetbrains.annotations.NotNull;

/**
 * The file type factory that declares types of git files
 */
public class GitFileTypeFactory extends FileTypeFactory {
  /**
   * {@inheritDoc}
   */
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(PlainTextFileType.INSTANCE, new ExactFileNameMatcher(".gitignore"), new ExactFileNameMatcher(".gitmodules"));
  }
}
