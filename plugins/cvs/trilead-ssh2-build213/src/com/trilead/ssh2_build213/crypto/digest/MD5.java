
package com.trilead.ssh2_build213.crypto.digest;

/**
 * MD5. Based on the example code in RFC 1321. Optimized (...a little).
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: MD5.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */

/*
 * The following disclaimer has been copied from RFC 1321:
 * 
 * Copyright (C) 1991-2, RSA Data Security, Inc. Created 1991. All rights
 * reserved.
 * 
 * License to copy and use this software is granted provided that it is
 * identified as the "RSA Data Security, Inc. MD5 Message-Digest Algorithm" in
 * all material mentioning or referencing this software or this function.
 * 
 * License is also granted to make and use derivative works provided that such
 * works are identified as "derived from the RSA Data Security, Inc. MD5
 * Message-Digest Algorithm" in all material mentioning or referencing the
 * derived work.
 * 
 * RSA Data Security, Inc. makes no representations concerning either the
 * merchantability of this software or the suitability of this software for any
 * particular purpose. It is provided "as is" without express or implied
 * warranty of any kind.
 * 
 * These notices must be retained in any copies of any part of this
 * documentation and/or software.
 * 
 */

public final class MD5 implements Digest
{
	private int state0, state1, state2, state3;
	private long count;
	private final byte[] block = new byte[64];
	private final int x[] = new int[16];

	private static final byte[] padding = new byte[] { (byte) 128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	public MD5()
	{
		reset();
	}

	private static final int FF(int a, int b, int c, int d, int x, int s, int ac)
	{
		a += ((b & c) | ((~b) & d)) + x + ac;
		return ((a << s) | (a >>> (32 - s))) + b;
	}

	private static final int GG(int a, int b, int c, int d, int x, int s, int ac)
	{
		a += ((b & d) | (c & (~d))) + x + ac;
		return ((a << s) | (a >>> (32 - s))) + b;
	}

	private static final int HH(int a, int b, int c, int d, int x, int s, int ac)
	{
		a += (b ^ c ^ d) + x + ac;
		return ((a << s) | (a >>> (32 - s))) + b;
	}

	private static final int II(int a, int b, int c, int d, int x, int s, int ac)
	{
		a += (c ^ (b | (~d))) + x + ac;
		return ((a << s) | (a >>> (32 - s))) + b;
	}

	private static final void encode(byte[] dst, int dstoff, int word)
	{
		dst[dstoff] = (byte) (word);
		dst[dstoff + 1] = (byte) (word >> 8);
		dst[dstoff + 2] = (byte) (word >> 16);
		dst[dstoff + 3] = (byte) (word >> 24);
	}

	private final void transform(byte[] src, int pos)
	{
		int a = state0;
		int b = state1;
		int c = state2;
		int d = state3;

		for (int i = 0; i < 16; i++, pos += 4)
		{
			x[i] = (src[pos] & 0xff) | ((src[pos + 1] & 0xff) << 8) | ((src[pos + 2] & 0xff) << 16)
					| ((src[pos + 3] & 0xff) << 24);
		}

		/* Round 1 */

		a = FF(a, b, c, d, x[0], 7, 0xd76aa478); /* 1 */
		d = FF(d, a, b, c, x[1], 12, 0xe8c7b756); /* 2 */
		c = FF(c, d, a, b, x[2], 17, 0x242070db); /* 3 */
		b = FF(b, c, d, a, x[3], 22, 0xc1bdceee); /* 4 */
		a = FF(a, b, c, d, x[4], 7, 0xf57c0faf); /* 5 */
		d = FF(d, a, b, c, x[5], 12, 0x4787c62a); /* 6 */
		c = FF(c, d, a, b, x[6], 17, 0xa8304613); /* 7 */
		b = FF(b, c, d, a, x[7], 22, 0xfd469501); /* 8 */
		a = FF(a, b, c, d, x[8], 7, 0x698098d8); /* 9 */
		d = FF(d, a, b, c, x[9], 12, 0x8b44f7af); /* 10 */
		c = FF(c, d, a, b, x[10], 17, 0xffff5bb1); /* 11 */
		b = FF(b, c, d, a, x[11], 22, 0x895cd7be); /* 12 */
		a = FF(a, b, c, d, x[12], 7, 0x6b901122); /* 13 */
		d = FF(d, a, b, c, x[13], 12, 0xfd987193); /* 14 */
		c = FF(c, d, a, b, x[14], 17, 0xa679438e); /* 15 */
		b = FF(b, c, d, a, x[15], 22, 0x49b40821); /* 16 */

		/* Round 2 */
		a = GG(a, b, c, d, x[1], 5, 0xf61e2562); /* 17 */
		d = GG(d, a, b, c, x[6], 9, 0xc040b340); /* 18 */
		c = GG(c, d, a, b, x[11], 14, 0x265e5a51); /* 19 */
		b = GG(b, c, d, a, x[0], 20, 0xe9b6c7aa); /* 20 */
		a = GG(a, b, c, d, x[5], 5, 0xd62f105d); /* 21 */
		d = GG(d, a, b, c, x[10], 9, 0x2441453); /* 22 */
		c = GG(c, d, a, b, x[15], 14, 0xd8a1e681); /* 23 */
		b = GG(b, c, d, a, x[4], 20, 0xe7d3fbc8); /* 24 */
		a = GG(a, b, c, d, x[9], 5, 0x21e1cde6); /* 25 */
		d = GG(d, a, b, c, x[14], 9, 0xc33707d6); /* 26 */
		c = GG(c, d, a, b, x[3], 14, 0xf4d50d87); /* 27 */
		b = GG(b, c, d, a, x[8], 20, 0x455a14ed); /* 28 */
		a = GG(a, b, c, d, x[13], 5, 0xa9e3e905); /* 29 */
		d = GG(d, a, b, c, x[2], 9, 0xfcefa3f8); /* 30 */
		c = GG(c, d, a, b, x[7], 14, 0x676f02d9); /* 31 */
		b = GG(b, c, d, a, x[12], 20, 0x8d2a4c8a); /* 32 */

		/* Round 3 */
		a = HH(a, b, c, d, x[5], 4, 0xfffa3942); /* 33 */
		d = HH(d, a, b, c, x[8], 11, 0x8771f681); /* 34 */
		c = HH(c, d, a, b, x[11], 16, 0x6d9d6122); /* 35 */
		b = HH(b, c, d, a, x[14], 23, 0xfde5380c); /* 36 */
		a = HH(a, b, c, d, x[1], 4, 0xa4beea44); /* 37 */
		d = HH(d, a, b, c, x[4], 11, 0x4bdecfa9); /* 38 */
		c = HH(c, d, a, b, x[7], 16, 0xf6bb4b60); /* 39 */
		b = HH(b, c, d, a, x[10], 23, 0xbebfbc70); /* 40 */
		a = HH(a, b, c, d, x[13], 4, 0x289b7ec6); /* 41 */
		d = HH(d, a, b, c, x[0], 11, 0xeaa127fa); /* 42 */
		c = HH(c, d, a, b, x[3], 16, 0xd4ef3085); /* 43 */
		b = HH(b, c, d, a, x[6], 23, 0x4881d05); /* 44 */
		a = HH(a, b, c, d, x[9], 4, 0xd9d4d039); /* 45 */
		d = HH(d, a, b, c, x[12], 11, 0xe6db99e5); /* 46 */
		c = HH(c, d, a, b, x[15], 16, 0x1fa27cf8); /* 47 */
		b = HH(b, c, d, a, x[2], 23, 0xc4ac5665); /* 48 */

		/* Round 4 */
		a = II(a, b, c, d, x[0], 6, 0xf4292244); /* 49 */
		d = II(d, a, b, c, x[7], 10, 0x432aff97); /* 50 */
		c = II(c, d, a, b, x[14], 15, 0xab9423a7); /* 51 */
		b = II(b, c, d, a, x[5], 21, 0xfc93a039); /* 52 */
		a = II(a, b, c, d, x[12], 6, 0x655b59c3); /* 53 */
		d = II(d, a, b, c, x[3], 10, 0x8f0ccc92); /* 54 */
		c = II(c, d, a, b, x[10], 15, 0xffeff47d); /* 55 */
		b = II(b, c, d, a, x[1], 21, 0x85845dd1); /* 56 */
		a = II(a, b, c, d, x[8], 6, 0x6fa87e4f); /* 57 */
		d = II(d, a, b, c, x[15], 10, 0xfe2ce6e0); /* 58 */
		c = II(c, d, a, b, x[6], 15, 0xa3014314); /* 59 */
		b = II(b, c, d, a, x[13], 21, 0x4e0811a1); /* 60 */
		a = II(a, b, c, d, x[4], 6, 0xf7537e82); /* 61 */
		d = II(d, a, b, c, x[11], 10, 0xbd3af235); /* 62 */
		c = II(c, d, a, b, x[2], 15, 0x2ad7d2bb); /* 63 */
		b = II(b, c, d, a, x[9], 21, 0xeb86d391); /* 64 */

		state0 += a;
		state1 += b;
		state2 += c;
		state3 += d;
	}

	public final void reset()
	{
		count = 0;

		state0 = 0x67452301;
		state1 = 0xefcdab89;
		state2 = 0x98badcfe;
		state3 = 0x10325476;

		/* Clear traces in memory... */

		for (int i = 0; i < 16; i++)
			x[i] = 0;
	}

	public final void update(byte b)
	{
		final int space = 64 - ((int) (count & 0x3f));

		count++;

		block[64 - space] = b;

		if (space == 1)
			transform(block, 0);
	}

	public final void update(byte[] buff, int pos, int len)
	{
		int space = 64 - ((int) (count & 0x3f));

		count += len;

		while (len > 0)
		{
			if (len < space)
			{
				System.arraycopy(buff, pos, block, 64 - space, len);
				break;
			}

			if (space == 64)
			{
				transform(buff, pos);
			}
			else
			{
				System.arraycopy(buff, pos, block, 64 - space, space);
				transform(block, 0);
			}

			pos += space;
			len -= space;
			space = 64;
		}
	}

	public final void update(byte[] b)
	{
		update(b, 0, b.length);
	}

	public final void digest(byte[] dst, int pos)
	{
		byte[] bits = new byte[8];

		encode(bits, 0, (int) (count << 3));
		encode(bits, 4, (int) (count >> 29));

		int idx = (int) count & 0x3f;
		int padLen = (idx < 56) ? (56 - idx) : (120 - idx);

		update(padding, 0, padLen);
		update(bits, 0, 8);

		encode(dst, pos, state0);
		encode(dst, pos + 4, state1);
		encode(dst, pos + 8, state2);
		encode(dst, pos + 12, state3);

		reset();
	}

	public final void digest(byte[] dst)
	{
		digest(dst, 0);
	}

	public final int getDigestLength()
	{
		return 16;
	}
}
