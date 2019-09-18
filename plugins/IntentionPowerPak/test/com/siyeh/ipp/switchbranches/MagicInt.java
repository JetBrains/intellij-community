import org.intellij.lang.annotations.MagicConstant;

class MagicInt {
    class Constants {
        static final int VAL1 = 10;
        static final int VAL2 = 20;
        static final int VAL3 = 30;
    }

    void test3(@MagicConstant(intValues = {Constants.VAL1, Constants.VAL2, Constants.VAL3}) int value) {
        switch<caret> (value) {
            case 20:break;
            case 21:break;
        }
    }
}