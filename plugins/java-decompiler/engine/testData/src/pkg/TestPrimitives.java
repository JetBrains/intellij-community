package pkg;

public class TestPrimitives {

  public void printAll() {
    printBoolean(true);
    printByte((byte) 123);
    printShort((short) 257);
    printInt(123);
    printLong(123L);
    printFloat(1.23F);
    printDouble(1.23);
    printChar('Z');

    System.out.println(String.format("%b, %d, %d, %d", getBoolean(), getByte(), getShort(), getInt()));
  }

  public void printBoolean(boolean b) {
    System.out.println(String.format("%b", b));
  }

  public void printByte(byte b) {
    System.out.println(String.format("%d", b));
  }

  public void printShort(short s) {
    System.out.println(String.format("%d", s));
  }

  public void printInt(int i) {
    System.out.println(String.format("%d", i));
  }

  public void printLong(long l) {
    System.out.println(String.format("%d", l));
  }

  public void printFloat(float f) {
    System.out.println(String.format("%f", f));
  }

  public void printDouble(double d) {
    System.out.println(String.format("%f", d));
  }

  public void printChar(char c) {
    System.out.println(String.format("%c", c));
  }


  public boolean getBoolean() {
    return false;
  }

  public byte getByte() {
    return (byte) 128;
  }

  public short getShort() {
    return (short) 32768;
  }

  public int getInt() {
    return 42;
  }

  public void printNarrowed() {
    printByte((byte)getInt());
    printShort((short)getInt());
  }
}
