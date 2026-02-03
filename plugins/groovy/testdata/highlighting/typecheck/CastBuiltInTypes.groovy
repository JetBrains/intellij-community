@groovy.transform.CompileStatic
class CastBuiltinTypes {

  def castObject(Object _) {
    (void) _
    (boolean) _
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    (Void) _
    (Boolean) _
    (Byte) _
    (Short) _
    (Character) _
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castNull() {
    <error descr="Cannot cast 'null' to 'void'">(void) null</error>
    (boolean) null
    <error descr="Cannot cast 'null' to 'byte'">(byte) null</error>
    <error descr="Cannot cast 'null' to 'short'">(short) null</error>
    <error descr="Cannot cast 'null' to 'char'">(char) null</error>
    <error descr="Cannot cast 'null' to 'int'">(int) null</error>
    <error descr="Cannot cast 'null' to 'long'">(long) null</error>
    <error descr="Cannot cast 'null' to 'float'">(float) null</error>
    <error descr="Cannot cast 'null' to 'double'">(double) null</error>
    (Void) null
    (Boolean) null
    (Byte) null
    (Short) null
    (Character) null
    (Integer) null
    (Long) null
    (Float) null
    (Double) null
    (BigInteger) null
    (BigDecimal) null
    (Object) null
  }

  void foo() {}

  def castvoid() {
    (void) foo()
    <error descr="Cannot cast 'void' to 'boolean'">(boolean) foo()</error>
    <error descr="Cannot cast 'void' to 'byte'">(byte) foo()</error>
    <error descr="Cannot cast 'void' to 'short'">(short) foo()</error>
    <error descr="Cannot cast 'void' to 'char'">(char) foo()</error>
    <error descr="Cannot cast 'void' to 'int'">(int) foo()</error>
    <error descr="Cannot cast 'void' to 'long'">(long) foo()</error>
    <error descr="Cannot cast 'void' to 'float'">(float) foo()</error>
    <error descr="Cannot cast 'void' to 'double'">(double) foo()</error>
    (Void) foo()
    <error descr="Cannot cast 'void' to 'Boolean'">(Boolean) foo()</error>
    <error descr="Cannot cast 'void' to 'Byte'">(Byte) foo()</error>
    <error descr="Cannot cast 'void' to 'Short'">(Short) foo()</error>
    <error descr="Cannot cast 'void' to 'Character'">(Character) foo()</error>
    <error descr="Cannot cast 'void' to 'Integer'">(Integer) foo()</error>
    <error descr="Cannot cast 'void' to 'Long'">(Long) foo()</error>
    <error descr="Cannot cast 'void' to 'Float'">(Float) foo()</error>
    <error descr="Cannot cast 'void' to 'Double'">(Double) foo()</error>
    <error descr="Cannot cast 'void' to 'BigInteger'">(BigInteger) foo()</error>
    <error descr="Cannot cast 'void' to 'BigDecimal'">(BigDecimal) foo()</error>
    <error descr="Cannot cast 'void' to 'Object'">(Object) foo()</error>
  }

  Void bar() {}

  def castVoid() {
    (void) bar()
    <error descr="Cannot cast 'Void' to 'boolean'">(boolean) bar()</error>
    <error descr="Cannot cast 'Void' to 'byte'">(byte) bar()</error>
    <error descr="Cannot cast 'Void' to 'short'">(short) bar()</error>
    <error descr="Cannot cast 'Void' to 'char'">(char) bar()</error>
    <error descr="Cannot cast 'Void' to 'int'">(int) bar()</error>
    <error descr="Cannot cast 'Void' to 'long'">(long) bar()</error>
    <error descr="Cannot cast 'Void' to 'float'">(float) bar()</error>
    <error descr="Cannot cast 'Void' to 'double'">(double) bar()</error>
    (Void) bar()
    <error descr="Cannot cast 'Void' to 'Boolean'">(Boolean) bar()</error>
    <error descr="Cannot cast 'Void' to 'Byte'">(Byte) bar()</error>
    <error descr="Cannot cast 'Void' to 'Short'">(Short) bar()</error>
    <error descr="Cannot cast 'Void' to 'Character'">(Character) bar()</error>
    <error descr="Cannot cast 'Void' to 'Integer'">(Integer) bar()</error>
    <error descr="Cannot cast 'Void' to 'Long'">(Long) bar()</error>
    <error descr="Cannot cast 'Void' to 'Float'">(Float) bar()</error>
    <error descr="Cannot cast 'Void' to 'Double'">(Double) bar()</error>
    <error descr="Cannot cast 'Void' to 'BigInteger'">(BigInteger) bar()</error>
    <error descr="Cannot cast 'Void' to 'BigDecimal'">(BigDecimal) bar()</error>
    (Object) bar()
  }

  def castboolean(boolean _) {
    <error descr="Cannot cast 'boolean' to 'void'">(void) _</error>
    (boolean) _
    <error descr="Cannot cast 'boolean' to 'byte'">(byte) _</error>
    <error descr="Cannot cast 'boolean' to 'short'">(short) _</error>
    <error descr="Cannot cast 'boolean' to 'char'">(char) _</error>
    <error descr="Cannot cast 'boolean' to 'int'">(int) _</error>
    <error descr="Cannot cast 'boolean' to 'long'">(long) _</error>
    <error descr="Cannot cast 'boolean' to 'float'">(float) _</error>
    <error descr="Cannot cast 'boolean' to 'double'">(double) _</error>
    <error descr="Cannot cast 'boolean' to 'Void'">(Void) _</error>
    (Boolean) _
    <error descr="Cannot cast 'boolean' to 'Byte'">(Byte) _</error>
    <error descr="Cannot cast 'boolean' to 'Short'">(Short) _</error>
    <error descr="Cannot cast 'boolean' to 'Character'">(Character) _</error>
    <error descr="Cannot cast 'boolean' to 'Integer'">(Integer) _</error>
    <error descr="Cannot cast 'boolean' to 'Long'">(Long) _</error>
    <error descr="Cannot cast 'boolean' to 'Float'">(Float) _</error>
    <error descr="Cannot cast 'boolean' to 'Double'">(Double) _</error>
    <error descr="Cannot cast 'boolean' to 'BigInteger'">(BigInteger) _</error>
    <error descr="Cannot cast 'boolean' to 'BigDecimal'">(BigDecimal) _</error>
    (Object) _
  }

  def castBoolean(Boolean _) {
    <error descr="Cannot cast 'Boolean' to 'void'">(void) _</error>
    (boolean) _
    <error descr="Cannot cast 'Boolean' to 'byte'">(byte) _</error>
    <error descr="Cannot cast 'Boolean' to 'short'">(short) _</error>
    <error descr="Cannot cast 'Boolean' to 'char'">(char) _</error>
    <error descr="Cannot cast 'Boolean' to 'int'">(int) _</error>
    <error descr="Cannot cast 'Boolean' to 'long'">(long) _</error>
    <error descr="Cannot cast 'Boolean' to 'float'">(float) _</error>
    <error descr="Cannot cast 'Boolean' to 'double'">(double) _</error>
    <error descr="Cannot cast 'Boolean' to 'Void'">(Void) _</error>
    (Boolean) _
    <error descr="Cannot cast 'Boolean' to 'Byte'">(Byte) _</error>
    <error descr="Cannot cast 'Boolean' to 'Short'">(Short) _</error>
    <error descr="Cannot cast 'Boolean' to 'Character'">(Character) _</error>
    <error descr="Cannot cast 'Boolean' to 'Integer'">(Integer) _</error>
    <error descr="Cannot cast 'Boolean' to 'Long'">(Long) _</error>
    <error descr="Cannot cast 'Boolean' to 'Float'">(Float) _</error>
    <error descr="Cannot cast 'Boolean' to 'Double'">(Double) _</error>
    <error descr="Cannot cast 'Boolean' to 'BigInteger'">(BigInteger) _</error>
    <error descr="Cannot cast 'Boolean' to 'BigDecimal'">(BigDecimal) _</error>
    (Object) _
  }

  def castCharacter(Character _) {
    <error descr="Cannot cast 'Character' to 'void'">(void) _</error>
    <error descr="Cannot cast 'Character' to 'boolean'">(boolean) _</error>
    <error descr="Cannot cast 'Character' to 'byte'">(byte) _</error>
    <error descr="Cannot cast 'Character' to 'short'">(short) _</error>
    (char) _
    <error descr="Cannot cast 'Character' to 'int'">(int) _</error>
    <error descr="Cannot cast 'Character' to 'long'">(long) _</error>
    <error descr="Cannot cast 'Character' to 'float'">(float) _</error>
    <error descr="Cannot cast 'Character' to 'double'">(double) _</error>
    <error descr="Cannot cast 'Character' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'Character' to 'Boolean'">(Boolean) _</error>
    <error descr="Cannot cast 'Character' to 'Byte'">(Byte) _</error>
    <error descr="Cannot cast 'Character' to 'Short'">(Short) _</error>
    (Character) _
    <error descr="Cannot cast 'Character' to 'Integer'">(Integer) _</error>
    <error descr="Cannot cast 'Character' to 'Long'">(Long) _</error>
    <error descr="Cannot cast 'Character' to 'Float'">(Float) _</error>
    <error descr="Cannot cast 'Character' to 'Double'">(Double) _</error>
    <error descr="Cannot cast 'Character' to 'BigInteger'">(BigInteger) _</error>
    <error descr="Cannot cast 'Character' to 'BigDecimal'">(BigDecimal) _</error>
    (Object) _
  }

  def castChar(char _) {
    <error descr="Cannot cast 'char' to 'void'">(void) _</error>
    <error descr="Cannot cast 'char' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'char' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'char' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    (Character) _
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castbyte(byte _) {
    <error descr="Cannot cast 'byte' to 'void'">(void) _</error>
    <error descr="Cannot cast 'byte' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'byte' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'byte' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'byte' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castshort(short _) {
    <error descr="Cannot cast 'short' to 'void'">(void) _</error>
    <error descr="Cannot cast 'short' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'short' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'short' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'short' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castInt(int _) {
    <error descr="Cannot cast 'int' to 'void'">(void) _</error>
    <error descr="Cannot cast 'int' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'int' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'int' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'int' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castlong(long _) {
    <error descr="Cannot cast 'long' to 'void'">(void) _</error>
    <error descr="Cannot cast 'long' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'long' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'long' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'long' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castfloat(float _) {
    <error descr="Cannot cast 'float' to 'void'">(void) _</error>
    <error descr="Cannot cast 'float' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'float' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'float' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'float' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castdouble(double _) {
    <error descr="Cannot cast 'double' to 'void'">(void) _</error>
    <error descr="Cannot cast 'double' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    (char) _
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'double' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'double' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'double' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castByte(Byte _) {
    <error descr="Cannot cast 'Byte' to 'void'">(void) _</error>
    <error descr="Cannot cast 'Byte' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'Byte' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'Byte' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'Byte' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'Byte' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castShort(Short _) {
    <error descr="Cannot cast 'Short' to 'void'">(void) _</error>
    <error descr="Cannot cast 'Short' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'Short' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'Short' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'Short' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'Short' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castInteger(Integer _) {
    <error descr="Cannot cast 'Integer' to 'void'">(void) _</error>
    <error descr="Cannot cast 'Integer' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'Integer' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'Integer' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'Integer' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'Integer' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castLong(Long _) {
    <error descr="Cannot cast 'Long' to 'void'">(void) _</error>
    <error descr="Cannot cast 'Long' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'Long' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'Long' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'Long' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'Long' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castFloat(Float _) {
    <error descr="Cannot cast 'Float' to 'void'">(void) _</error>
    <error descr="Cannot cast 'Float' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'Float' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'Float' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'Float' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'Float' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castDouble(Double _) {
    <error descr="Cannot cast 'Double' to 'void'">(void) _</error>
    <error descr="Cannot cast 'Double' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'Double' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'Double' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'Double' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'Double' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castBigInteger(BigInteger _) {
    <error descr="Cannot cast 'BigInteger' to 'void'">(void) _</error>
    <error descr="Cannot cast 'BigInteger' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'BigInteger' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'BigInteger' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'BigInteger' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'BigInteger' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }

  def castBigDecimal(BigDecimal _) {
    <error descr="Cannot cast 'BigDecimal' to 'void'">(void) _</error>
    <error descr="Cannot cast 'BigDecimal' to 'boolean'">(boolean) _</error>
    (byte) _
    (short) _
    <error descr="Cannot cast 'BigDecimal' to 'char'">(char) _</error>
    (int) _
    (long) _
    (float) _
    (double) _
    <error descr="Cannot cast 'BigDecimal' to 'Void'">(Void) _</error>
    <error descr="Cannot cast 'BigDecimal' to 'Boolean'">(Boolean) _</error>
    (Byte) _
    (Short) _
    <error descr="Cannot cast 'BigDecimal' to 'Character'">(Character) _</error>
    (Integer) _
    (Long) _
    (Float) _
    (Double) _
    (BigInteger) _
    (BigDecimal) _
    (Object) _
  }
}