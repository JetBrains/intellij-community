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

package org.java_websocket.framing;

import org.java_websocket.enums.Opcode;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.util.ByteBufferUtils;

import java.nio.ByteBuffer;

/**
 * Abstract implementation of a frame
 */
public abstract class FramedataImpl1 implements Framedata {

    /**
     * Indicates that this is the final fragment in a message.
     */
    private boolean fin;
    /**
     * Defines the interpretation of the "Payload data".
     */
    private Opcode optcode;

    /**
     * The unmasked "Payload data" which was sent in this frame
     */
    private ByteBuffer unmaskedpayload;

    /**
     * Defines whether the "Payload data" is masked.
     */
    private boolean transferemasked;

    /**
     * Indicates that the rsv1 bit is set or not
     */
    private boolean rsv1;

    /**
     * Indicates that the rsv2 bit is set or not
     */
    private boolean rsv2;

    /**
     * Indicates that the rsv3 bit is set or not
     */
    private boolean rsv3;

    /**
     * Check if the frame is valid due to specification
     *
     * @throws InvalidDataException thrown if the frame is not a valid frame
     */
    public abstract void isValid() throws InvalidDataException;

    /**
     * Constructor for a FramedataImpl without any attributes set apart from the opcode
     *
     * @param op the opcode to use
     */
    public FramedataImpl1(Opcode op) {
        optcode = op;
        unmaskedpayload = ByteBufferUtils.getEmptyByteBuffer();
        fin = true;
        transferemasked = false;
        rsv1 = false;
        rsv2 = false;
        rsv3 = false;
    }

    @Override
    public boolean isRSV1() {
        return rsv1;
    }

    @Override
    public boolean isRSV2() {
        return rsv2;
    }

    @Override
    public boolean isRSV3() {
        return rsv3;
    }

    @Override
    public boolean isFin() {
        return fin;
    }

    @Override
    public Opcode getOpcode() {
        return optcode;
    }

    @Override
    public boolean getTransfereMasked() {
        return transferemasked;
    }

    @Override
    public ByteBuffer getPayloadData() {
        return unmaskedpayload;
    }

    @Override
    public void append(Framedata nextframe) {
        ByteBuffer b = nextframe.getPayloadData();
        if (unmaskedpayload == null) {
            unmaskedpayload = ByteBuffer.allocate(b.remaining());
            b.mark();
            unmaskedpayload.put(b);
            b.reset();
        } else {
            b.mark();
            unmaskedpayload.position(unmaskedpayload.limit());
            unmaskedpayload.limit(unmaskedpayload.capacity());

            if (b.remaining() > unmaskedpayload.remaining()) {
                ByteBuffer tmp = ByteBuffer.allocate(b.remaining() + unmaskedpayload.capacity());
                unmaskedpayload.flip();
                tmp.put(unmaskedpayload);
                tmp.put(b);
                unmaskedpayload = tmp;

            } else {
                unmaskedpayload.put(b);
            }
            unmaskedpayload.rewind();
            b.reset();
        }
        fin = nextframe.isFin();

    }

    @Override
    public String toString() {
        return "Framedata{ optcode:" + getOpcode() + ", fin:" + isFin() + ", rsv1:" + isRSV1() + ", rsv2:" + isRSV2() + ", rsv3:" + isRSV3() + ", payloadlength:[pos:" + unmaskedpayload.position() + ", len:" + unmaskedpayload.remaining() + "], payload:" + ( unmaskedpayload.remaining() > 1000 ? "(too big to display)" : new String( unmaskedpayload.array() ) ) + '}';
    }

    /**
     * Set the payload of this frame to the provided payload
     *
     * @param payload the payload which is to set
     */
    public void setPayload(ByteBuffer payload) {
        this.unmaskedpayload = payload;
    }

    /**
     * Set the fin of this frame to the provided boolean
     *
     * @param fin true if fin has to be set
     */
    public void setFin(boolean fin) {
        this.fin = fin;
    }

    /**
     * Set the rsv1 of this frame to the provided boolean
     *
     * @param rsv1 true if fin has to be set
     */
    public void setRSV1(boolean rsv1) {
        this.rsv1 = rsv1;
    }

    /**
     * Set the rsv2 of this frame to the provided boolean
     *
     * @param rsv2 true if fin has to be set
     */
    public void setRSV2(boolean rsv2) {
        this.rsv2 = rsv2;
    }

    /**
     * Set the rsv3 of this frame to the provided boolean
     *
     * @param rsv3 true if fin has to be set
     */
    public void setRSV3(boolean rsv3) {
        this.rsv3 = rsv3;
    }

    /**
     * Set the tranferemask of this frame to the provided boolean
     *
     * @param transferemasked true if transferemasked has to be set
     */
    public void setTransferemasked(boolean transferemasked) {
        this.transferemasked = transferemasked;
    }

    /**
     * Get a frame with a specific opcode
     *
     * @param opcode the opcode representing the frame
     * @return the frame with a specific opcode
     */
    public static FramedataImpl1 get(Opcode opcode) {
        if (opcode== null) {
            throw new IllegalArgumentException("Supplied opcode cannot be null");
        }
        switch (opcode) {
            case PING:
                return new PingFrame();
            case PONG:
                return new PongFrame();
            case TEXT:
                return new TextFrame();
            case BINARY:
                return new BinaryFrame();
            case CLOSING:
                return new CloseFrame();
            case CONTINUOUS:
                return new ContinuousFrame();
            default:
                throw new IllegalArgumentException("Supplied opcode is invalid");
        }
    }

    @Override
    public boolean equals( Object o ) {
        if( this == o ) return true;
        if( o == null || getClass() != o.getClass() ) return false;

        FramedataImpl1 that = (FramedataImpl1) o;

        if( fin != that.fin ) return false;
        if( transferemasked != that.transferemasked ) return false;
        if( rsv1 != that.rsv1 ) return false;
        if( rsv2 != that.rsv2 ) return false;
        if( rsv3 != that.rsv3 ) return false;
        if( optcode != that.optcode ) return false;
        return unmaskedpayload != null ? unmaskedpayload.equals( that.unmaskedpayload ) : that.unmaskedpayload == null;
    }

    @Override
    public int hashCode() {
        int result = ( fin ? 1 : 0 );
        result = 31 * result + optcode.hashCode();
        result = 31 * result + ( unmaskedpayload != null ? unmaskedpayload.hashCode() : 0 );
        result = 31 * result + ( transferemasked ? 1 : 0 );
        result = 31 * result + ( rsv1 ? 1 : 0 );
        result = 31 * result + ( rsv2 ? 1 : 0 );
        result = 31 * result + ( rsv3 ? 1 : 0 );
        return result;
    }
}
