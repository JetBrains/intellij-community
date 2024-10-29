package pkg;

public class TestCompoundAssignment {
    public int testSimple(int i, int j) {
        i += j;
        i -= j;
        i *= j;
        i /= j;
        i &= j;
        i |= j;
        i ^= j;
        i >>= j;
        i <<= j;
        i >>>= j;
        
        return i;
    }

    public int testComplex(int i, int j, int k) {
        i += j + k;
        i -= j + k;
        i *= j + k;
        i /= j + k;
        i &= j + k;
        i |= j + k;
        i ^= j + k;
        i >>= j + k;
        i <<= j + k;
        i >>>= j + k;

        return i;
    }

    public int testComplexParens(int i, int j, int k, int m) {
        i += (j + k) * m;
        i -= (j + k) * m;
        i *= (j + k) * m;
        i /= (j + k) * m;
        i &= (j + k) * m;
        i |= (j + k) * m;
        i ^= (j + k) * m;
        i >>= (j + k) * m;
        i <<= (j + k) * m;
        i >>>= (j + k) * m;

        return i;
    }

    public int testComplexTernary(int i, int j, int k, int m, boolean b) {
        i += b ? j : k * m;
        i -= b ? j : k * m;
        i *= b ? j : k * m;
        i /= b ? j : k * m;
        i &= b ? j : k * m;
        i |= b ? j : k * m;
        i ^= b ? j : k * m;
        i >>= b ? j : k * m;
        i <<= b ? j : k * m;
        i >>>= b ? j : k * m;

        return i;
    }

    public int testArrayOp(int i, int j, int[] a, int b) {
        i += a[b] = j;
        i -= a[b] = j;
        i *= a[b] = j;
        i /= a[b] = j;
        i &= a[b] = j;
        i |= a[b] = j;
        i ^= a[b] = j;
        i >>= a[b] = j;
        i <<= a[b] = j;
        i >>>= a[b] = j;

        return i;
    }
}
