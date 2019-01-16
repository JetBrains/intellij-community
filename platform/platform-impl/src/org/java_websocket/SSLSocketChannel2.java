/*
 * Copyright (c) 2010-2018 Nathan Rajlich
 *
 *  Permission is hereby granted, free of charge, to any person
 *  obtaining a copy of this software and associated documentation
 *  files (the "Software"), to deal in the Software without
 *  restriction, including without limitation the rights to use,
 *  copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following
 *  conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 *  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE.
 */
package org.java_websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Implements the relevant portions of the SocketChannel interface with the SSLEngine wrapper.
 */
public class SSLSocketChannel2 implements ByteChannel, WrappedByteChannel {

    /**
     * Logger instance
     *
     * @since 1.4.0
     */
    private static final Logger log = LoggerFactory.getLogger(SSLSocketChannel2.class);

    /**
     * This object is used to feed the {@link SSLEngine}'s wrap and unwrap methods during the handshake phase.
     **/
    protected static ByteBuffer emptybuffer = ByteBuffer.allocate( 0 );

    protected ExecutorService exec;

    protected List<Future<?>> tasks;

    /** raw payload incomming */
    protected ByteBuffer inData;
    /** encrypted data outgoing */
    protected ByteBuffer outCrypt;
    /** encrypted data incoming */
    protected ByteBuffer inCrypt;

    /** the underlying channel */
    protected SocketChannel socketChannel;
    /** used to set interestOP SelectionKey.OP_WRITE for the underlying channel */
    protected SelectionKey selectionKey;

    protected SSLEngine sslEngine;
    protected SSLEngineResult readEngineResult;
    protected SSLEngineResult writeEngineResult;

    /**
     * Should be used to count the buffer allocations.
     * But because of #190 where HandshakeStatus.FINISHED is not properly returned by nio wrap/unwrap this variable is used to check whether {@link #createBuffers(SSLSession)} needs to be called.
     **/
    protected int bufferallocations = 0;

    public SSLSocketChannel2( SocketChannel channel , SSLEngine sslEngine , ExecutorService exec , SelectionKey key ) throws IOException {
        if( channel == null || sslEngine == null || exec == null )
            throw new IllegalArgumentException( "parameter must not be null" );

        this.socketChannel = channel;
        this.sslEngine = sslEngine;
        this.exec = exec;

        readEngineResult = writeEngineResult = new SSLEngineResult( Status.BUFFER_UNDERFLOW, sslEngine.getHandshakeStatus(), 0, 0 ); // init to prevent NPEs

        tasks = new ArrayList<Future<?>>( 3 );
        if( key != null ) {
            key.interestOps( key.interestOps() | SelectionKey.OP_WRITE );
            this.selectionKey = key;
        }
        createBuffers( sslEngine.getSession() );
        // kick off handshake
        socketChannel.write( wrap( emptybuffer ) );// initializes res
        processHandshake();
    }

    private void consumeFutureUninterruptible( Future<?> f ) {
        try {
            while ( true ) {
                try {
                    f.get();
                    break;
                } catch ( InterruptedException e ) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch ( ExecutionException e ) {
            throw new RuntimeException( e );
        }
    }

    /**
     * This method will do whatever necessary to process the sslengine handshake.
     * Thats why it's called both from the {@link #read(ByteBuffer)} and {@link #write(ByteBuffer)}
     **/
    private synchronized void processHandshake() throws IOException {
        if( sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING )
            return; // since this may be called either from a reading or a writing thread and because this method is synchronized it is necessary to double check if we are still handshaking.
        if( !tasks.isEmpty() ) {
            Iterator<Future<?>> it = tasks.iterator();
            while ( it.hasNext() ) {
                Future<?> f = it.next();
                if( f.isDone() ) {
                    it.remove();
                } else {
                    if( isBlocking() )
                        consumeFutureUninterruptible( f );
                    return;
                }
            }
        }

        if( sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP ) {
            if( !isBlocking() || readEngineResult.getStatus() == Status.BUFFER_UNDERFLOW ) {
                inCrypt.compact();
                int read = socketChannel.read( inCrypt );
                if( read == -1 ) {
                    throw new IOException( "connection closed unexpectedly by peer" );
                }
                inCrypt.flip();
            }
            inData.compact();
            unwrap();
            if( readEngineResult.getHandshakeStatus() == HandshakeStatus.FINISHED ) {
                createBuffers( sslEngine.getSession() );
                return;
            }
        }
        consumeDelegatedTasks();
        if( tasks.isEmpty() || sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP ) {
            socketChannel.write( wrap( emptybuffer ) );
            if( writeEngineResult.getHandshakeStatus() == HandshakeStatus.FINISHED ) {
                createBuffers( sslEngine.getSession() );
                return;
            }
        }
        assert ( sslEngine.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING );// this function could only leave NOT_HANDSHAKING after createBuffers was called unless #190 occurs which means that nio wrap/unwrap never return HandshakeStatus.FINISHED

        bufferallocations = 1; // look at variable declaration why this line exists and #190. Without this line buffers would not be be recreated when #190 AND a rehandshake occur.
    }
    private synchronized ByteBuffer wrap( ByteBuffer b ) throws SSLException {
        outCrypt.compact();
        writeEngineResult = sslEngine.wrap( b, outCrypt );
        outCrypt.flip();
        return outCrypt;
    }

    /**
     * performs the unwrap operation by unwrapping from {@link #inCrypt} to {@link #inData}
     **/
    private synchronized ByteBuffer unwrap() throws SSLException {
        int rem;
        //There are some ssl test suites, which get around the selector.select() call, which cause an infinite unwrap and 100% cpu usage (see #459 and #458)
        if(readEngineResult.getStatus() == Status.CLOSED && sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING){
            try {
                close();
            } catch (IOException e) {
                //Not really interesting
            }
        }
        do {
            rem = inData.remaining();
            readEngineResult = sslEngine.unwrap( inCrypt, inData );
        } while ( readEngineResult.getStatus() == Status.OK && ( rem != inData.remaining() || sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP ) );
        inData.flip();
        return inData;
    }

    protected void consumeDelegatedTasks() {
        Runnable task;
        while ( ( task = sslEngine.getDelegatedTask() ) != null ) {
            tasks.add( exec.submit( task ) );
            // task.run();
        }
    }

    protected void createBuffers( SSLSession session ) {
        int netBufferMax = session.getPacketBufferSize();
        int appBufferMax = Math.max(session.getApplicationBufferSize(), netBufferMax);

        if( inData == null ) {
            inData = ByteBuffer.allocate( appBufferMax );
            outCrypt = ByteBuffer.allocate( netBufferMax );
            inCrypt = ByteBuffer.allocate( netBufferMax );
        } else {
            if( inData.capacity() != appBufferMax )
                inData = ByteBuffer.allocate( appBufferMax );
            if( outCrypt.capacity() != netBufferMax )
                outCrypt = ByteBuffer.allocate( netBufferMax );
            if( inCrypt.capacity() != netBufferMax )
                inCrypt = ByteBuffer.allocate( netBufferMax );
        }
        if (inData.remaining() != 0 && log.isTraceEnabled()) {
            log.trace(new String( inData.array(), inData.position(), inData.remaining()));
        }
        inData.rewind();
        inData.flip();
        if (inCrypt.remaining() != 0 && log.isTraceEnabled()) {
            log.trace(new String( inCrypt.array(), inCrypt.position(), inCrypt.remaining()));
        }
        inCrypt.rewind();
        inCrypt.flip();
        outCrypt.rewind();
        outCrypt.flip();
        bufferallocations++;
    }

    public int write( ByteBuffer src ) throws IOException {
        if( !isHandShakeComplete() ) {
            processHandshake();
            return 0;
        }
        // assert ( bufferallocations > 1 ); //see #190
        //if( bufferallocations <= 1 ) {
        //	createBuffers( sslEngine.getSession() );
        //}
        int num = socketChannel.write( wrap( src ) );
        if (writeEngineResult.getStatus() == Status.CLOSED) {
            throw new EOFException("Connection is closed");
        }
        return num;

    }

    /**
     * Blocks when in blocking mode until at least one byte has been decoded.<br>
     * When not in blocking mode 0 may be returned.
     *
     * @return the number of bytes read.
     **/
    public int read(ByteBuffer dst) throws IOException {
        while (true) {
            if (!dst.hasRemaining())
                return 0;
            if (!isHandShakeComplete()) {
                if (isBlocking()) {
                    while (!isHandShakeComplete()) {
                        processHandshake();
                    }
                } else {
                    processHandshake();
                    if (!isHandShakeComplete()) {
                        return 0;
                    }
                }
            }
            // assert ( bufferallocations > 1 ); //see #190
            //if( bufferallocations <= 1 ) {
            //	createBuffers( sslEngine.getSession() );
            //}
		/* 1. When "dst" is smaller than "inData" readRemaining will fill "dst" with data decoded in a previous read call.
		 * 2. When "inCrypt" contains more data than "inData" has remaining space, unwrap has to be called on more time(readRemaining)
		 */
            int purged = readRemaining(dst);
            if (purged != 0)
                return purged;

		/* We only continue when we really need more data from the network.
		 * Thats the case if inData is empty or inCrypt holds to less data than necessary for decryption
		 */
            assert (inData.position() == 0);
            inData.clear();

            if (!inCrypt.hasRemaining())
                inCrypt.clear();
            else
                inCrypt.compact();

            if (isBlocking() || readEngineResult.getStatus() == Status.BUFFER_UNDERFLOW)
                if (socketChannel.read(inCrypt) == -1) {
                    return -1;
                }
            inCrypt.flip();
            unwrap();

            int transfered = transfereTo(inData, dst);
            if (transfered == 0 && isBlocking()) {
                continue;
            }
            return transfered;
        }
    }
    /**
     * {@link #read(ByteBuffer)} may not be to leave all buffers(inData, inCrypt)
     **/
    private int readRemaining( ByteBuffer dst ) throws SSLException {
        if( inData.hasRemaining() ) {
            return transfereTo( inData, dst );
        }
        if( !inData.hasRemaining() )
            inData.clear();
        // test if some bytes left from last read (e.g. BUFFER_UNDERFLOW)
        if( inCrypt.hasRemaining() ) {
            unwrap();
            int amount = transfereTo( inData, dst );
            if (readEngineResult.getStatus() == Status.CLOSED) {
                return -1;
            }
            if( amount > 0 )
                return amount;
        }
        return 0;
    }

    public boolean isConnected() {
        return socketChannel.isConnected();
    }

    public void close() throws IOException {
        sslEngine.closeOutbound();
        sslEngine.getSession().invalidate();
        if( socketChannel.isOpen() )
            socketChannel.write( wrap( emptybuffer ) );// FIXME what if not all bytes can be written
        socketChannel.close();
    }

    private boolean isHandShakeComplete() {
        HandshakeStatus status = sslEngine.getHandshakeStatus();
        return status == HandshakeStatus.FINISHED || status == HandshakeStatus.NOT_HANDSHAKING;
    }

    public SelectableChannel configureBlocking( boolean b ) throws IOException {
        return socketChannel.configureBlocking( b );
    }

    public boolean connect( SocketAddress remote ) throws IOException {
        return socketChannel.connect( remote );
    }

    public boolean finishConnect() throws IOException {
        return socketChannel.finishConnect();
    }

    public Socket socket() {
        return socketChannel.socket();
    }

    public boolean isInboundDone() {
        return sslEngine.isInboundDone();
    }

    @Override
    public boolean isOpen() {
        return socketChannel.isOpen();
    }

    @Override
    public boolean isNeedWrite() {
        return outCrypt.hasRemaining() || !isHandShakeComplete(); // FIXME this condition can cause high cpu load during handshaking when network is slow
    }

    @Override
    public void writeMore() throws IOException {
        write( outCrypt );
    }

    @Override
    public boolean isNeedRead() {
        return inData.hasRemaining() || ( inCrypt.hasRemaining() && readEngineResult.getStatus() != Status.BUFFER_UNDERFLOW && readEngineResult.getStatus() != Status.CLOSED );
    }

    @Override
    public int readMore( ByteBuffer dst ) throws SSLException {
        return readRemaining( dst );
    }

    private int transfereTo( ByteBuffer from, ByteBuffer to ) {
        int fremain = from.remaining();
        int toremain = to.remaining();
        if( fremain > toremain ) {
            // FIXME there should be a more efficient transfer method
            int limit = Math.min( fremain, toremain );
            for( int i = 0 ; i < limit ; i++ ) {
                to.put( from.get() );
            }
            return limit;
        } else {
            to.put( from );
            return fremain;
        }

    }

    @Override
    public boolean isBlocking() {
        return socketChannel.isBlocking();
    }

}