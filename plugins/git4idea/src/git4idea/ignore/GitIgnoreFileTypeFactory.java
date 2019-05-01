/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package git4idea.ignore;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType;
import git4idea.ignore.lang.GitIgnoreLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Class that assigns file types with languages.
 */
public class GitIgnoreFileTypeFactory extends FileTypeFactory {
  /**
   * Assigns file types with languages.
   *
   * @param consumer file types consumer
   */
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consume(consumer, GitIgnoreLanguage.INSTANCE.getFileType());
  }

  /**
   * Shorthand for consuming ignore file types.
   *
   * @param consumer file types consumer
   * @param fileType file type to consume
   */
  private static void consume(@NotNull FileTypeConsumer consumer, @NotNull IgnoreFileType fileType) {
    consumer.consume(fileType, fileType.getIgnoreLanguage().getExtension());
  }
}
