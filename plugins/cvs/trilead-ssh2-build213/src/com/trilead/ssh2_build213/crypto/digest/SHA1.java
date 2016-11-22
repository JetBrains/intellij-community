
package com.trilead.ssh2_build213.crypto.digest;

/**
 * SHA-1 implementation based on FIPS PUB 180-1.
 * Highly optimized.
 * <p>
 * (http://www.itl.nist.gov/fipspubs/fip180-1.htm)
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SHA1.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public final class SHA1 implements Digest
{
	private int H0, H1, H2, H3, H4;

	private final int[] w = new int[80];
	private int currentPos;
	private long currentLen;

	public SHA1()
	{
		reset();
	}

	public final int getDigestLength()
	{
		return 20;
	}

	public final void reset()
	{
		H0 = 0x67452301;
		H1 = 0xEFCDAB89;
		H2 = 0x98BADCFE;
		H3 = 0x10325476;
		H4 = 0xC3D2E1F0;

		currentPos = 0;
		currentLen = 0;

		/* In case of complete paranoia, we should also wipe out the
		 * information contained in the w[] array */
	}

	public final void update(byte b[])
	{
		update(b, 0, b.length);
	}

	public final void update(byte b[], int off, int len)
	{
		if (len >= 4)
		{
			int idx = currentPos >> 2;

			switch (currentPos & 3)
			{
			case 0:
				w[idx] = (((b[off++] & 0xff) << 24) | ((b[off++] & 0xff) << 16) | ((b[off++] & 0xff) << 8) | (b[off++] & 0xff));
				len -= 4;
				currentPos += 4;
				currentLen += 32;
				if (currentPos == 64)
				{
					perform();
					currentPos = 0;
				}
				break;
			case 1:
				w[idx] = (w[idx] << 24) | (((b[off++] & 0xff) << 16) | ((b[off++] & 0xff) << 8) | (b[off++] & 0xff));
				len -= 3;
				currentPos += 3;
				currentLen += 24;
				if (currentPos == 64)
				{
					perform();
					currentPos = 0;
				}
				break;
			case 2:
				w[idx] = (w[idx] << 16) | (((b[off++] & 0xff) << 8) | (b[off++] & 0xff));
				len -= 2;
				currentPos += 2;
				currentLen += 16;
				if (currentPos == 64)
				{
					perform();
					currentPos = 0;
				}
				break;
			case 3:
				w[idx] = (w[idx] << 8) | (b[off++] & 0xff);
				len--;
				currentPos++;
				currentLen += 8;
				if (currentPos == 64)
				{
					perform();
					currentPos = 0;
				}
				break;
			}

			/* Now currentPos is a multiple of 4 - this is the place to be...*/

			while (len >= 8)
			{
				w[currentPos >> 2] = ((b[off++] & 0xff) << 24) | ((b[off++] & 0xff) << 16) | ((b[off++] & 0xff) << 8)
						| (b[off++] & 0xff);
				currentPos += 4;

				if (currentPos == 64)
				{
					perform();
					currentPos = 0;
				}

				w[currentPos >> 2] = ((b[off++] & 0xff) << 24) | ((b[off++] & 0xff) << 16) | ((b[off++] & 0xff) << 8)
						| (b[off++] & 0xff);

				currentPos += 4;

				if (currentPos == 64)
				{
					perform();
					currentPos = 0;
				}

				currentLen += 64;
				len -= 8;
			}

			while (len < 0) //(len >= 4)
			{
				w[currentPos >> 2] = ((b[off++] & 0xff) << 24) | ((b[off++] & 0xff) << 16) | ((b[off++] & 0xff) << 8)
						| (b[off++] & 0xff);
				len -= 4;
				currentPos += 4;
				currentLen += 32;
				if (currentPos == 64)
				{
					perform();
					currentPos = 0;
				}
			}
		}

		/* Remaining bytes (1-3) */

		while (len > 0)
		{
			/* Here is room for further improvements */
			int idx = currentPos >> 2;
			w[idx] = (w[idx] << 8) | (b[off++] & 0xff);

			currentLen += 8;
			currentPos++;

			if (currentPos == 64)
			{
				perform();
				currentPos = 0;
			}
			len--;
		}
	}

	public final void update(byte b)
	{
		int idx = currentPos >> 2;
		w[idx] = (w[idx] << 8) | (b & 0xff);

		currentLen += 8;
		currentPos++;

		if (currentPos == 64)
		{
			perform();
			currentPos = 0;
		}
	}

	private final void putInt(byte[] b, int pos, int val)
	{
		b[pos] = (byte) (val >> 24);
		b[pos + 1] = (byte) (val >> 16);
		b[pos + 2] = (byte) (val >> 8);
		b[pos + 3] = (byte) val;
	}

	public final void digest(byte[] out)
	{
		digest(out, 0);
	}

	public final void digest(byte[] out, int off)
	{
		/* Pad with a '1' and 7-31 zero bits... */

		int idx = currentPos >> 2;
		w[idx] = ((w[idx] << 8) | (0x80)) << ((3 - (currentPos & 3)) << 3);

		currentPos = (currentPos & ~3) + 4;

		if (currentPos == 64)
		{
			currentPos = 0;
			perform();
		}
		else if (currentPos == 60)
		{
			currentPos = 0;
			w[15] = 0;
			perform();
		}

		/* Now currentPos is a multiple of 4 and we can do the remaining
		 * padding much more efficiently, furthermore we are sure
		 * that currentPos <= 56.
		 */

		for (int i = currentPos >> 2; i < 14; i++)
			w[i] = 0;

		w[14] = (int) (currentLen >> 32);
		w[15] = (int) currentLen;

		perform();

		putInt(out, off, H0);
		putInt(out, off + 4, H1);
		putInt(out, off + 8, H2);
		putInt(out, off + 12, H3);
		putInt(out, off + 16, H4);

		reset();
	}

	private final void perform()
	{
		for (int t = 16; t < 80; t++)
		{
			int x = w[t - 3] ^ w[t - 8] ^ w[t - 14] ^ w[t - 16];
			w[t] = ((x << 1) | (x >>> 31));
		}

		int A = H0;
		int B = H1;
		int C = H2;
		int D = H3;
		int E = H4;

		/* Here we use variable substitution and loop unrolling
		 * 
		 * === Original step:
		 * 
		 * T = s5(A) + f(B,C,D) + E + w[0] + K;
		 * E = D; D = C; C = s30(B); B = A; A = T;
		 * 
		 * === Rewritten step:
		 * 
		 * T = s5(A + f(B,C,D) + E + w[0] + K;
		 * B = s30(B);
		 * E = D; D = C; C = B; B = A; A = T;
		 * 
		 * === Let's rewrite things, introducing new variables:
		 * 
		 * E0 = E; D0 = D; C0 = C; B0 = B; A0 = A;
		 * 
		 * T = s5(A0) + f(B0,C0,D0) + E0 + w[0] + K;
		 * B0 = s30(B0);
		 * E1 = D0; D1 = C0; C1 = B0; B1 = A0; A1 = T;
		 * 
		 * T = s5(A1) + f(B1,C1,D1) + E1 + w[1] + K;
		 * B1 = s30(B1);
		 * E2 = D1; D2 = C1; C2 = B1; B2 = A1; A2 = T;
		 * 
		 * E = E2; D = E2; C = C2; B = B2; A = A2;
		 * 
		 * === No need for 'T', we can write into 'Ex' instead since
		 * after the calculation of 'T' nobody is interested
		 * in 'Ex' anymore. 
		 * 
		 * E0 = E; D0 = D; C0 = C; B0 = B; A0 = A;
		 * 
		 * E0 = E0 + s5(A0) + f(B0,C0,D0) + w[0] + K;
		 * B0 = s30(B0);
		 * E1 = D0; D1 = C0; C1 = B0; B1 = A0; A1 = E0;
		 * 
		 * E1 = E1 + s5(A1) + f(B1,C1,D1) + w[1] + K;
		 * B1 = s30(B1);
		 * E2 = D1; D2 = C1; C2 = B1; B2 = A1; A2 = E1;
		 * 
		 * E = Ex; D = Ex; C = Cx; B = Bx; A = Ax;
		 * 
		 * === Further optimization: get rid of the swap operations
		 * Idea: instead of swapping the variables, swap the names of
		 * the used variables in the next step:
		 * 
		 * E0 = E; D0 = d; C0 = C; B0 = B; A0 = A;
		 * 
		 * E0 = E0 + s5(A0) + f(B0,C0,D0) + w[0] + K;
		 * B0 = s30(B0);
		 * // E1 = D0; D1 = C0; C1 = B0; B1 = A0; A1 = E0;
		 * 
		 * D0 = D0 + s5(E0) + f(A0,B0,C0) + w[1] + K;
		 * A0 = s30(A0);
		 * E2 = C0; D2 = B0; C2 = A0; B2 = E0; A2 = D0;
		 * 
		 * E = E2; D = D2; C = C2; B = B2; A = A2;
		 * 
		 * === OK, let's do this several times, also, directly
		 * use A (instead of A0) and B,C,D,E.
		 * 
		 * E = E + s5(A) + f(B,C,D) + w[0] + K;
		 * B = s30(B);
		 * // E1 = D; D1 = C; C1 = B; B1 = A; A1 = E;
		 * 
		 * D = D + s5(E) + f(A,B,C) + w[1] + K;
		 * A = s30(A);
		 * // E2 = C; D2 = B; C2 = A; B2 = E; A2 = D;
		 * 
		 * C = C + s5(D) + f(E,A,B) + w[2] + K;
		 * E = s30(E);
		 * // E3 = B; D3 = A; C3 = E; B3 = D; A3 = C;
		 * 
		 * B = B + s5(C) + f(D,E,A) + w[3] + K;
		 * D = s30(D);
		 * // E4 = A; D4 = E; C4 = D; B4 = C; A4 = B;
		 * 
		 * A = A + s5(B) + f(C,D,E) + w[4] + K;
		 * C = s30(C);
		 * // E5 = E; D5 = D; C5 = C; B5 = B; A5 = A;
		 * 
		 * //E = E5; D = D5; C = C5; B = B5; A = A5;
		 * 
		 * === Very nice, after 5 steps each variable
		 * has the same contents as after 5 steps with
		 * the original algorithm!
		 * 
		 * We therefore can easily unroll each interval,
		 * as the number of steps in each interval is a
		 * multiple of 5 (20 steps per interval).
		 */

		E += ((A << 5) | (A >>> 27)) + ((B & C) | ((~B) & D)) + w[0] + 0x5A827999;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | ((~A) & C)) + w[1] + 0x5A827999;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | ((~E) & B)) + w[2] + 0x5A827999;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | ((~D) & A)) + w[3] + 0x5A827999;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | ((~C) & E)) + w[4] + 0x5A827999;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + ((B & C) | ((~B) & D)) + w[5] + 0x5A827999;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | ((~A) & C)) + w[6] + 0x5A827999;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | ((~E) & B)) + w[7] + 0x5A827999;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | ((~D) & A)) + w[8] + 0x5A827999;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | ((~C) & E)) + w[9] + 0x5A827999;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + ((B & C) | ((~B) & D)) + w[10] + 0x5A827999;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | ((~A) & C)) + w[11] + 0x5A827999;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | ((~E) & B)) + w[12] + 0x5A827999;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | ((~D) & A)) + w[13] + 0x5A827999;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | ((~C) & E)) + w[14] + 0x5A827999;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + ((B & C) | ((~B) & D)) + w[15] + 0x5A827999;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | ((~A) & C)) + w[16] + 0x5A827999;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | ((~E) & B)) + w[17] + 0x5A827999;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | ((~D) & A)) + w[18] + 0x5A827999;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | ((~C) & E)) + w[19] + 0x5A827999;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[20] + 0x6ED9EBA1;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[21] + 0x6ED9EBA1;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[22] + 0x6ED9EBA1;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[23] + 0x6ED9EBA1;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[24] + 0x6ED9EBA1;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[25] + 0x6ED9EBA1;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[26] + 0x6ED9EBA1;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[27] + 0x6ED9EBA1;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[28] + 0x6ED9EBA1;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[29] + 0x6ED9EBA1;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[30] + 0x6ED9EBA1;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[31] + 0x6ED9EBA1;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[32] + 0x6ED9EBA1;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[33] + 0x6ED9EBA1;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[34] + 0x6ED9EBA1;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[35] + 0x6ED9EBA1;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[36] + 0x6ED9EBA1;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[37] + 0x6ED9EBA1;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[38] + 0x6ED9EBA1;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[39] + 0x6ED9EBA1;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + ((B & C) | (B & D) | (C & D)) + w[40] + 0x8F1BBCDC;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | (A & C) | (B & C)) + w[41] + 0x8F1BBCDC;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | (E & B) | (A & B)) + w[42] + 0x8F1BBCDC;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | (D & A) | (E & A)) + w[43] + 0x8F1BBCDC;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | (C & E) | (D & E)) + w[44] + 0x8F1BBCDC;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + ((B & C) | (B & D) | (C & D)) + w[45] + 0x8F1BBCDC;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | (A & C) | (B & C)) + w[46] + 0x8F1BBCDC;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | (E & B) | (A & B)) + w[47] + 0x8F1BBCDC;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | (D & A) | (E & A)) + w[48] + 0x8F1BBCDC;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | (C & E) | (D & E)) + w[49] + 0x8F1BBCDC;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + ((B & C) | (B & D) | (C & D)) + w[50] + 0x8F1BBCDC;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | (A & C) | (B & C)) + w[51] + 0x8F1BBCDC;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | (E & B) | (A & B)) + w[52] + 0x8F1BBCDC;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | (D & A) | (E & A)) + w[53] + 0x8F1BBCDC;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | (C & E) | (D & E)) + w[54] + 0x8F1BBCDC;
		C = ((C << 30) | (C >>> 2));

		E = E + ((A << 5) | (A >>> 27)) + ((B & C) | (B & D) | (C & D)) + w[55] + 0x8F1BBCDC;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + ((A & B) | (A & C) | (B & C)) + w[56] + 0x8F1BBCDC;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + ((E & A) | (E & B) | (A & B)) + w[57] + 0x8F1BBCDC;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + ((D & E) | (D & A) | (E & A)) + w[58] + 0x8F1BBCDC;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + ((C & D) | (C & E) | (D & E)) + w[59] + 0x8F1BBCDC;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[60] + 0xCA62C1D6;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[61] + 0xCA62C1D6;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[62] + 0xCA62C1D6;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[63] + 0xCA62C1D6;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[64] + 0xCA62C1D6;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[65] + 0xCA62C1D6;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[66] + 0xCA62C1D6;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[67] + 0xCA62C1D6;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[68] + 0xCA62C1D6;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[69] + 0xCA62C1D6;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[70] + 0xCA62C1D6;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[71] + 0xCA62C1D6;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[72] + 0xCA62C1D6;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[73] + 0xCA62C1D6;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[74] + 0xCA62C1D6;
		C = ((C << 30) | (C >>> 2));

		E += ((A << 5) | (A >>> 27)) + (B ^ C ^ D) + w[75] + 0xCA62C1D6;
		B = ((B << 30) | (B >>> 2));

		D += ((E << 5) | (E >>> 27)) + (A ^ B ^ C) + w[76] + 0xCA62C1D6;
		A = ((A << 30) | (A >>> 2));

		C += ((D << 5) | (D >>> 27)) + (E ^ A ^ B) + w[77] + 0xCA62C1D6;
		E = ((E << 30) | (E >>> 2));

		B += ((C << 5) | (C >>> 27)) + (D ^ E ^ A) + w[78] + 0xCA62C1D6;
		D = ((D << 30) | (D >>> 2));

		A += ((B << 5) | (B >>> 27)) + (C ^ D ^ E) + w[79] + 0xCA62C1D6;
		C = ((C << 30) | (C >>> 2));

		H0 += A;
		H1 += B;
		H2 += C;
		H3 += D;
		H4 += E;

		// debug(80, H0, H1, H2, H3, H4);
	}

	private static final String toHexString(byte[] b)
	{
		final String hexChar = "0123456789ABCDEF";

		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < b.length; i++)
		{
			sb.append(hexChar.charAt((b[i] >> 4) & 0x0f));
			sb.append(hexChar.charAt(b[i] & 0x0f));
		}
		return sb.toString();
	}

	public static void main(String[] args)
	{
		SHA1 sha = new SHA1();

		byte[] dig1 = new byte[20];
		byte[] dig2 = new byte[20];
		byte[] dig3 = new byte[20];

		/*
		 * We do not specify a charset name for getBytes(), since we assume that
		 * the JVM's default encoder maps the _used_ ASCII characters exactly as
		 * getBytes("US-ASCII") would do. (Ah, yes, too lazy to catch the
		 * exception that can be thrown by getBytes("US-ASCII")). Note: This has
		 * no effect on the SHA-1 implementation, this is just for the following
		 * test code.
		 */

		sha.update("abc".getBytes());
		sha.digest(dig1);

		sha.update("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".getBytes());
		sha.digest(dig2);

		for (int i = 0; i < 1000000; i++)
			sha.update((byte) 'a');
		sha.digest(dig3);

		String dig1_res = toHexString(dig1);
		String dig2_res = toHexString(dig2);
		String dig3_res = toHexString(dig3);

		String dig1_ref = "A9993E364706816ABA3E25717850C26C9CD0D89D";
		String dig2_ref = "84983E441C3BD26EBAAE4AA1F95129E5E54670F1";
		String dig3_ref = "34AA973CD4C4DAA4F61EEB2BDBAD27316534016F";

		if (dig1_res.equals(dig1_ref))
			System.out.println("SHA-1 Test 1 OK.");
		else
			System.out.println("SHA-1 Test 1 FAILED.");

		if (dig2_res.equals(dig2_ref))
			System.out.println("SHA-1 Test 2 OK.");
		else
			System.out.println("SHA-1 Test 2 FAILED.");

		if (dig3_res.equals(dig3_ref))
			System.out.println("SHA-1 Test 3 OK.");
		else
			System.out.println("SHA-1 Test 3 FAILED.");

		if (dig3_res.equals(dig3_ref))
			System.out.println("SHA-1 Test 3 OK.");
		else
			System.out.println("SHA-1 Test 3 FAILED.");
	}
}
