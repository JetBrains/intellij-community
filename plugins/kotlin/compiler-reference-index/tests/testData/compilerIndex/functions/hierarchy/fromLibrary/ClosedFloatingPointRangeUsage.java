import kotlin.ranges.RangesKt;

class ClosedFloatingPointRangeUsage {
    void t() {
        boolean flag = RangesKt.rangeTo(4.0, 3.0).contains(1.0);
    }
}