/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.io;

import java.io.IOException;
import java.io.InputStream;

public class MappedFileInputStream extends InputStream {
  private ResizeableMappedFile raf;
  private int cur;
  private long limit;

  public MappedFileInputStream(final ResizeableMappedFile raf, final long pos, final long limit) {
    this.raf = raf;
    setup(pos, limit);
  }

  public void setup(final long pos, final long limit) {
    this.cur = (int)pos;
    this.limit = limit;
  }

  public MappedFileInputStream(final ResizeableMappedFile raf, final long pos) throws IOException {
    this(raf, pos, raf.length());
  }

  public int available()
  {
      return (int)(limit - cur);
  }

  public void close()
  {
      //do nothing because we want to leave the random access file open.
  }

  public int read() throws IOException
  {
    int retval = -1;
    if( cur < limit )
    {
        retval = raf.get(cur++);
    }
    return retval;
  }

  public int read( byte[] b, int offset, int length ) throws IOException
  {
      //only allow a read of the amount available.
      if( length > available() )
      {
          length = available();
      }

      if( available() > 0 )
      {
        raf.get(cur, b, offset, length);
        cur += length;
      }

      return length;
  }

  public long skip( long amountToSkip )
  {
      long amountSkipped = Math.min( amountToSkip, available() );
      cur+= amountSkipped;
      return amountSkipped;
  }
}
