package pkg;

import java.lang.Math;

public class TestIffSimplification {
    public int simpleIff(boolean status, int[] values) {
        return status ? values[0] : values[1];
    }

    public int simpleIf(boolean status, int[] values) {
        if (status) {
            return values[0];
        }
        else {
            return values[1];
        }
    }

    public int nestedIf(boolean status, boolean condition, int[] values) {
        if (status) {
            if (condition) {
                return values[2];
            }
            else {
                return values[0];
            }
        }
        else {
            return values[1];
        }
    }

    public int compareTo(int mc1, int mc2, byte csg1, byte csg2, double score1, double score2, int doc1, int doc2) {
        if (mc1 != mc2) {
            return mc1 < mc2 ? 1 : -1;
        }

        if (csg1 != csg2) {
            return csg1 < csg2 ? 1 : -1;
        }

        if (Math.abs(score1 - score2) < 1e-6) {
            return doc1 < doc2 ? -1 : 1;
        }

        return score1 < score2 ? 1 : -1;
    }
}