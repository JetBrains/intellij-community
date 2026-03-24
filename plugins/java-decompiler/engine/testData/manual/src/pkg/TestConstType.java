package pkg;

public class TestConstType {
    private char lineBreak = '\n';
    private char zero = 0;

    public void setLineBreak(char os) {
        switch (os) {
            case 'u':
                lineBreak = '\r';
                break;

            case 'w':
                lineBreak = '\n';
                break;
        }
    }

    public void init() {
        setLineBreak('w');
    }

    public String convertIndentation(String text) {
        if (text.charAt(0) == '\t') {
            text = text.replace('\t', ' ');
        }
        return text;
    }

    public void printalot() {
        System.out.println('a');
        System.out.println('\t');

        System.out.println(0);
        System.out.println(65);
        System.out.println(120);
        System.out.println(32760);
        System.out.println(32761);
        System.out.println(35000);
        System.out.println(50000);
        System.out.println(128000);
        System.out.println(60793);
        System.out.println(60737);
        System.out.println(60777);
        System.out.println(60785);
        System.out.println(60835);
        System.out.println(60843);
        System.out.println(60851);
        System.out.println(60859);
        System.out.println(1048576);
        System.out.println(49152);
        System.out.println(44100);
        System.out.println(44101);
        System.out.println(44102);
        System.out.println(44103);
        System.out.println(60000);
        System.out.println(64000);
        System.out.println(65000);
        System.out.println(45000);
    }

    public char guessType(int val) {
        if (0 <= val && val <= 127) {
            return 'X';
        }
        else if (-128 <= val && val <= 127) {
            return 'B';
        }
        else if (128 <= val && val <= 32767) {
            return 'Y';
        }
        else if (-32768 <= val && val <= 32767) {
            return 'S';
        }
        else if (32768 <= val && val <= 0xFFFF) {
            return 'C';
        }
        else {
            return 'I';
        }
    }

    public int getTypeMaxValue(char type) {
        int maxValue;
        switch (type) {
            case 'X':
                maxValue = 128;
                break;
            case 'B':
                maxValue = 127;
                break;
            case 'Y':
                maxValue = 32768;
                break;
            case 'S':
                maxValue = 32767;
                break;
            case 'C':
                maxValue = 0xFFFF;
                break;
            default:
                maxValue = Integer.MAX_VALUE;
        }
        return maxValue;
    }
}
