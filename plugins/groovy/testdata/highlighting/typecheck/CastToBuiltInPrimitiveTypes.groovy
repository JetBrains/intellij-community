import groovy.transform.CompileStatic

@CompileStatic
class FooCastToBuiltinPrimitiveTypes {

  def castToVoid() {
    <error descr="Cannot cast 'null' to 'void'">(void) null</error>
    (void) 1 as void        // no error, can cast void to void
    <error descr="Cannot cast 'boolean' to 'void'">(void) 1 as boolean</error>
    <error descr="Cannot cast 'byte' to 'void'">(void) 1 as byte</error>
    <error descr="Cannot cast 'short' to 'void'">(void) 1 as short</error>
    <error descr="Cannot cast 'char' to 'void'">(void) 1 as char</error>
    <error descr="Cannot cast 'int' to 'void'">(void) 1 as int</error>
    <error descr="Cannot cast 'long' to 'void'">(void) 1 as long</error>
    <error descr="Cannot cast 'float' to 'void'">(void) 1 as float</error>
    <error descr="Cannot cast 'double' to 'void'">(void) 1 as double</error>
    <error descr="Cannot cast 'BigInteger' to 'void'">(void) 1 as BigInteger</error>
    <error descr="Cannot cast 'BigDecimal' to 'void'">(void) 1 as BigDecimal</error>
    <error descr="Cannot cast 'Void' to 'void'">(void) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'void'">(void) 1 as Boolean</error>
    <error descr="Cannot cast 'Byte' to 'void'">(void) 1 as Byte</error>
    <error descr="Cannot cast 'Short' to 'void'">(void) 1 as Short</error>
    <error descr="Cannot cast 'Character' to 'void'">(void) 1 as Character</error>
    <error descr="Cannot cast 'Integer' to 'void'">(void) 1 as Integer</error>
    <error descr="Cannot cast 'Long' to 'void'">(void) 1 as Long</error>
    <error descr="Cannot cast 'Float' to 'void'">(void) 1 as Float</error>
    <error descr="Cannot cast 'Double' to 'void'">(void) 1 as Double</error>
    <warning descr="Cannot cast 'Object' to 'void'">(void) 1 as Object</warning>        // no compile time, but will be runtime error
  }

  def castToBoolean() {
    (boolean) null
    <error descr="Cannot cast 'void' to 'boolean'">(boolean) 1 as void</error>
    (boolean) 1 as boolean
    <error descr="Cannot cast 'byte' to 'boolean'">(boolean) 1 as byte</error>
    <error descr="Cannot cast 'short' to 'boolean'">(boolean) 1 as short</error>
    <error descr="Cannot cast 'char' to 'boolean'">(boolean) 1 as char</error>
    <error descr="Cannot cast 'int' to 'boolean'">(boolean) 1 as int</error>
    <error descr="Cannot cast 'long' to 'boolean'">(boolean) 1 as long</error>
    <error descr="Cannot cast 'float' to 'boolean'">(boolean) 1 as float</error>
    <error descr="Cannot cast 'double' to 'boolean'">(boolean) 1 as double</error>
    <error descr="Cannot cast 'BigInteger' to 'boolean'">(boolean) 1 as BigInteger</error>
    <error descr="Cannot cast 'BigDecimal' to 'boolean'">(boolean) 1 as BigDecimal</error>
    <error descr="Cannot cast 'Void' to 'boolean'">(boolean) 1 as Void</error>
    (boolean) 1 as Boolean
    <error descr="Cannot cast 'Byte' to 'boolean'">(boolean) 1 as Byte</error>
    <error descr="Cannot cast 'Short' to 'boolean'">(boolean) 1 as Short</error>
    <error descr="Cannot cast 'Character' to 'boolean'">(boolean) 1 as Character</error>
    <error descr="Cannot cast 'Integer' to 'boolean'">(boolean) 1 as Integer</error>
    <error descr="Cannot cast 'Long' to 'boolean'">(boolean) 1 as Long</error>
    <error descr="Cannot cast 'Float' to 'boolean'">(boolean) 1 as Float</error>
    <error descr="Cannot cast 'Double' to 'boolean'">(boolean) 1 as Double</error>
    (boolean) 1 as Object
  }

  def castToByte() {
    <error descr="Cannot cast 'null' to 'byte'">(byte) null</error>
    <error descr="Cannot cast 'void' to 'byte'">(byte) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'byte'">(byte) 1 as boolean</error>
    (byte) 1 as byte
    (byte) 1 as short
    (byte) 1 as char
    (byte) 1 as int
    (byte) 1 as long
    (byte) 1 as float
    (byte) 1 as double
    (byte) 1 as BigInteger
    (byte) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'byte'">(byte) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'byte'">(byte) 1 as Boolean</error>
    (byte) 1 as Byte
    (byte) 1 as Short
    <error descr="Cannot cast 'Character' to 'byte'">(byte) 1 as Character</error>
    (byte) 1 as Integer
    (byte) 1 as Long
    (byte) 1 as Float
    (byte) 1 as Double
    (byte) 1 as Object
  }

  def castToShort() {
    <error descr="Cannot cast 'null' to 'short'">(short) null</error>
    <error descr="Cannot cast 'void' to 'short'">(short) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'short'">(short) 1 as boolean</error>
    (short) 1 as byte
    (short) 1 as short
    (short) 1 as char
    (short) 1 as int
    (short) 1 as long
    (short) 1 as float
    (short) 1 as double
    (short) 1 as BigInteger
    (short) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'short'">(short) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'short'">(short) 1 as Boolean</error>
    (short) 1 as Byte
    (short) 1 as Short
    <error descr="Cannot cast 'Character' to 'short'">(short) 1 as Character</error>
    (short) 1 as Integer
    (short) 1 as Long
    (short) 1 as Float
    (short) 1 as Double
    (short) 1 as Object
  }

  def castToChar() {
    <error descr="Cannot cast 'null' to 'char'">(char) null</error>
    <error descr="Cannot cast 'void' to 'char'">(char) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'char'">(char) 1 as boolean</error>
    (char) 1 as byte
    (char) 1 as short
    (char) 1 as char
    (char) 1 as int
    (char) 1 as long
    (char) 1 as float
    (char) 1 as double
    <error descr="Cannot cast 'BigInteger' to 'char'">(char) 1 as BigInteger</error>
    <error descr="Cannot cast 'BigDecimal' to 'char'">(char) 1 as BigDecimal</error>
    <error descr="Cannot cast 'Void' to 'char'">(char) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'char'">(char) 1 as Boolean</error>
    <error descr="Cannot cast 'Byte' to 'char'">(char) 1 as Byte</error>
    <error descr="Cannot cast 'Short' to 'char'">(char) 1 as Short</error>
    (char) 1 as Character
    <error descr="Cannot cast 'Integer' to 'char'">(char) 1 as Integer</error>
    <error descr="Cannot cast 'Long' to 'char'">(char) 1 as Long</error>
    <error descr="Cannot cast 'Float' to 'char'">(char) 1 as Float</error>
    <error descr="Cannot cast 'Double' to 'char'">(char) 1 as Double</error>
    (char) 1 as Object
  }

  def castToInt() {
    <error descr="Cannot cast 'null' to 'int'">(int) null</error>
    <error descr="Cannot cast 'void' to 'int'">(int) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'int'">(int) 1 as boolean</error>
    (int) 1 as byte
    (int) 1 as short
    (int) 1 as char
    (int) 1 as int
    (int) 1 as long
    (int) 1 as float
    (int) 1 as double
    (int) 1 as BigInteger
    (int) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'int'">(int) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'int'">(int) 1 as Boolean</error>
    (int) 1 as Byte
    (int) 1 as Short
    <error descr="Cannot cast 'Character' to 'int'">(int) 1 as Character</error>
    (int) 1 as Integer
    (int) 1 as Long
    (int) 1 as Float
    (int) 1 as Double
    (int) 1 as Object
  }

  def castToLong() {
    <error descr="Cannot cast 'null' to 'long'">(long) null</error>
    <error descr="Cannot cast 'void' to 'long'">(long) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'long'">(long) 1 as boolean</error>
    (long) 1 as byte
    (long) 1 as short
    (long) 1 as char
    (long) 1 as int
    (long) 1 as long
    (long) 1 as float
    (long) 1 as double
    (long) 1 as BigInteger
    (long) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'long'">(long) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'long'">(long) 1 as Boolean</error>
    (long) 1 as Byte
    (long) 1 as Short
    <error descr="Cannot cast 'Character' to 'long'">(long) 1 as Character</error>
    (long) 1 as Integer
    (long) 1 as Long
    (long) 1 as Float
    (long) 1 as Double
    (long) 1 as Object
  }

  def castToFloat() {
    <error descr="Cannot cast 'null' to 'float'">(float) null</error>
    <error descr="Cannot cast 'void' to 'float'">(float) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'float'">(float) 1 as boolean</error>
    (float) 1 as byte
    (float) 1 as short
    (float) 1 as char
    (float) 1 as int
    (float) 1 as long
    (float) 1 as float
    (float) 1 as double
    (float) 1 as BigInteger
    (float) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'float'">(float) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'float'">(float) 1 as Boolean</error>
    (float) 1 as Byte
    (float) 1 as Short
    <error descr="Cannot cast 'Character' to 'float'">(float) 1 as Character</error>
    (float) 1 as Integer
    (float) 1 as Long
    (float) 1 as Float
    (float) 1 as Double
    (float) 1 as Object
  }

  def castToDouble() {
    <error descr="Cannot cast 'null' to 'double'">(double) null</error>
    <error descr="Cannot cast 'void' to 'double'">(double) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'double'">(double) 1 as boolean</error>
    (double) 1 as byte
    (double) 1 as short
    (double) 1 as char
    (double) 1 as int
    (double) 1 as long
    (double) 1 as float
    (double) 1 as double
    (double) 1 as BigInteger
    (double) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'double'">(double) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'double'">(double) 1 as Boolean</error>
    (double) 1 as Byte
    (double) 1 as Short
    <error descr="Cannot cast 'Character' to 'double'">(double) 1 as Character</error>
    (double) 1 as Integer
    (double) 1 as Long
    (double) 1 as Float
    (double) 1 as Double
    (double) 1 as Object
  }
}
