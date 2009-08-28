/*
 * @author max
 */
package com.intellij.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class RandomAccessFileInputStream extends InputStream {
  private RandomAccessFile raf;
  private long cur;
  private long limit;

  public RandomAccessFileInputStream(final RandomAccessFile raf, final long pos, final long limit) {
    this.raf = raf;
    setup(pos, limit);
  }

  public void setup(final long pos, final long limit) {
    this.cur = pos;
    this.limit = limit;
  }

  public RandomAccessFileInputStream(final RandomAccessFile raf, final long pos) throws IOException {
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
        raf.seek( cur );
        cur++;
        retval = raf.read();
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
      int amountRead = -1;
      //only read if there are bytes actually available, otherwise
      //return -1 if the EOF has been reached.
      if( available() > 0 )
      {
        raf.seek( cur );
        amountRead = raf.read( b, offset, length );
      }
      //update the current cursor position.
      if( amountRead > 0 )
      {
          cur += amountRead;
      }
      return amountRead;
  }

  public long skip( long amountToSkip )
  {
      long amountSkipped = Math.min( amountToSkip, available() );
      cur+= amountSkipped;
      return amountSkipped;
  }
}