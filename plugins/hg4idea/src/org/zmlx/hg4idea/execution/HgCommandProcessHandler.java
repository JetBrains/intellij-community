/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.execution;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BinaryOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

class HgCommandProcessHandler extends OSProcessHandler {
  //todo move to common handlers place and reuse for svn and Gnuplot runner
  private final boolean myBinary;
  private final ByteArrayOutputStream myBinaryOutput;

  public HgCommandProcessHandler(@NotNull final Process process,
                                 @Nullable final String commandLine,
                                 @Nullable final Charset charset,
                                 boolean isBinary) {
    super(process, commandLine, charset);
    myBinary = isBinary;
    myBinaryOutput = new ByteArrayOutputStream();
  }

  @NotNull
  @Override
  protected BaseDataReader createOutputDataReader(BaseDataReader.SleepingPolicy sleepingPolicy) {
    return myBinary ? new MyBinaryOutputReader(myProcess.getInputStream(), sleepingPolicy) : super.createOutputDataReader(sleepingPolicy);
  }

  @NotNull
  public ByteArrayOutputStream getBinaryOutput() {
    return myBinaryOutput;
  }

  private class MyBinaryOutputReader extends BinaryOutputReader {

    public MyBinaryOutputReader(@NotNull InputStream stream, @Nullable BaseDataReader.SleepingPolicy simple) {
      super(stream, simple);
      start();
    }

    @Override
    protected void onBinaryAvailable(@NotNull byte[] data, int size) {
      myBinaryOutput.write(data, 0, size);
    }

    @Override
    protected Future<?> executeOnPooledThread(Runnable runnable) {
      return HgCommandProcessHandler.this.executeOnPooledThread(runnable);
    }
  }
}
