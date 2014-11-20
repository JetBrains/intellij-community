import groovy.transform.CompileStatic

@CompileStatic
class FooCastToBuiltinBoxedTypes {

  def castToVoidB() {
    (Void) null
    (Void) 1 as void
    <error descr="Cannot cast 'boolean' to 'Void'">(Void) 1 as boolean</error>
    <error descr="Cannot cast 'byte' to 'Void'">(Void) 1 as byte</error>
    <error descr="Cannot cast 'short' to 'Void'">(Void) 1 as short</error>
    <error descr="Cannot cast 'char' to 'Void'">(Void) 1 as char</error>
    <error descr="Cannot cast 'int' to 'Void'">(Void) 1 as int</error>
    <error descr="Cannot cast 'long' to 'Void'">(Void) 1 as long</error>
    <error descr="Cannot cast 'float' to 'Void'">(Void) 1 as float</error>
    <error descr="Cannot cast 'double' to 'Void'">(Void) 1 as double</error>
    <error descr="Cannot cast 'BigInteger' to 'Void'">(Void) 1 as BigInteger</error>
    <error descr="Cannot cast 'BigDecimal' to 'Void'">(Void) 1 as BigDecimal</error>
    (Void) 1 as Void
    <error descr="Cannot cast 'Boolean' to 'Void'">(Void) 1 as Boolean</error>
    <error descr="Cannot cast 'Byte' to 'Void'">(Void) 1 as Byte</error>
    <error descr="Cannot cast 'Short' to 'Void'">(Void) 1 as Short</error>
    <error descr="Cannot cast 'Character' to 'Void'">(Void) 1 as Character</error>
    <error descr="Cannot cast 'Integer' to 'Void'">(Void) 1 as Integer</error>
    <error descr="Cannot cast 'Long' to 'Void'">(Void) 1 as Long</error>
    <error descr="Cannot cast 'Float' to 'Void'">(Void) 1 as Float</error>
    <error descr="Cannot cast 'Double' to 'Void'">(Void) 1 as Double</error>
    <warning descr="Cannot cast 'Object' to 'Void'">(Void) 1 as Object</warning>
  }

  def castToBigInteger() {
    (BigInteger) null
    <error descr="Cannot cast 'void' to 'BigInteger'">(BigInteger) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'BigInteger'">(BigInteger) 1 as boolean</error>
    (BigInteger) 1 as byte
    (BigInteger) 1 as short
    (BigInteger) 1 as char
    (BigInteger) 1 as int
    (BigInteger) 1 as long
    (BigInteger) 1 as float
    (BigInteger) 1 as double
    (BigInteger) 1 as BigInteger
    (BigInteger) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'BigInteger'">(BigInteger) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'BigInteger'">(BigInteger) 1 as Boolean</error>
    (BigInteger) 1 as Byte
    (BigInteger) 1 as Short
    <error descr="Cannot cast 'Character' to 'BigInteger'">(BigInteger) 1 as Character</error>
    (BigInteger) 1 as Integer
    (BigInteger) 1 as Long
    (BigInteger) 1 as Float
    (BigInteger) 1 as Double
    (BigInteger) 1 as Object
  }

  def castToBigDecimal() {
    (BigDecimal) null
    <error descr="Cannot cast 'void' to 'BigDecimal'">(BigDecimal) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'BigDecimal'">(BigDecimal) 1 as boolean</error>
    (BigDecimal) 1 as byte
    (BigDecimal) 1 as short
    (BigDecimal) 1 as char
    (BigDecimal) 1 as int
    (BigDecimal) 1 as long
    (BigDecimal) 1 as float
    (BigDecimal) 1 as double
    (BigDecimal) 1 as BigInteger
    (BigDecimal) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'BigDecimal'">(BigDecimal) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'BigDecimal'">(BigDecimal) 1 as Boolean</error>
    (BigDecimal) 1 as Byte
    (BigDecimal) 1 as Short
    <error descr="Cannot cast 'Character' to 'BigDecimal'">(BigDecimal) 1 as Character</error>
    (BigDecimal) 1 as Integer
    (BigDecimal) 1 as Long
    (BigDecimal) 1 as Float
    (BigDecimal) 1 as Double
    (BigDecimal) 1 as Object
  }

  def castToBoolean() {
    (Boolean) null
    <error descr="Cannot cast 'void' to 'Boolean'">(Boolean) 1 as void</error>
    (Boolean) 1 as boolean
    <error descr="Cannot cast 'byte' to 'Boolean'">(Boolean) 1 as byte</error>
    <error descr="Cannot cast 'short' to 'Boolean'">(Boolean) 1 as short</error>
    <error descr="Cannot cast 'char' to 'Boolean'">(Boolean) 1 as char</error>
    <error descr="Cannot cast 'int' to 'Boolean'">(Boolean) 1 as int</error>
    <error descr="Cannot cast 'long' to 'Boolean'">(Boolean) 1 as long</error>
    <error descr="Cannot cast 'float' to 'Boolean'">(Boolean) 1 as float</error>
    <error descr="Cannot cast 'double' to 'Boolean'">(Boolean) 1 as double</error>
    <error descr="Cannot cast 'BigInteger' to 'Boolean'">(Boolean) 1 as BigInteger</error>
    <error descr="Cannot cast 'BigDecimal' to 'Boolean'">(Boolean) 1 as BigDecimal</error>
    <error descr="Cannot cast 'Void' to 'Boolean'">(Boolean) 1 as Void</error>
    (Boolean) 1 as Boolean
    <error descr="Cannot cast 'Byte' to 'Boolean'">(Boolean) 1 as Byte</error>
    <error descr="Cannot cast 'Short' to 'Boolean'">(Boolean) 1 as Short</error>
    <error descr="Cannot cast 'Character' to 'Boolean'">(Boolean) 1 as Character</error>
    <error descr="Cannot cast 'Integer' to 'Boolean'">(Boolean) 1 as Integer</error>
    <error descr="Cannot cast 'Long' to 'Boolean'">(Boolean) 1 as Long</error>
    <error descr="Cannot cast 'Float' to 'Boolean'">(Boolean) 1 as Float</error>
    <error descr="Cannot cast 'Double' to 'Boolean'">(Boolean) 1 as Double</error>
    (Boolean) 1 as Object
  }

  def castToByte() {
    (Byte) null
    <error descr="Cannot cast 'void' to 'Byte'">(Byte) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'Byte'">(Byte) 1 as boolean</error>
    (Byte) 1 as byte
    (Byte) 1 as short
    (Byte) 1 as char
    (Byte) 1 as int
    (Byte) 1 as long
    (Byte) 1 as float
    (Byte) 1 as double
    (Byte) 1 as BigInteger
    (Byte) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'Byte'">(Byte) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'Byte'">(Byte) 1 as Boolean</error>
    (Byte) 1 as Byte
    (Byte) 1 as Short
    <error descr="Cannot cast 'Character' to 'Byte'">(Byte) 1 as Character</error>
    (Byte) 1 as Integer
    (Byte) 1 as Long
    (Byte) 1 as Float
    (Byte) 1 as Double
    (Byte) 1 as Object
  }

  def castToShort() {
    (Short) null
    <error descr="Cannot cast 'void' to 'Short'">(Short) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'Short'">(Short) 1 as boolean</error>
    (Short) 1 as byte
    (Short) 1 as short
    (Short) 1 as char
    (Short) 1 as int
    (Short) 1 as long
    (Short) 1 as float
    (Short) 1 as double
    (Short) 1 as BigInteger
    (Short) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'Short'">(Short) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'Short'">(Short) 1 as Boolean</error>
    (Short) 1 as Byte
    (Short) 1 as Short
    <error descr="Cannot cast 'Character' to 'Short'">(Short) 1 as Character</error>
    (Short) 1 as Integer
    (Short) 1 as Long
    (Short) 1 as Float
    (Short) 1 as Double
    (Short) 1 as Object
  }

  def castToChar() {
    (Character) null
    <error descr="Cannot cast 'void' to 'Character'">(Character) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'Character'">(Character) 1 as boolean</error>
    <error descr="Cannot cast 'byte' to 'Character'">(Character) 1 as byte</error>
    <error descr="Cannot cast 'short' to 'Character'">(Character) 1 as short</error>
    (Character) 1 as char
    <error descr="Cannot cast 'int' to 'Character'">(Character) 1 as int</error>
    <error descr="Cannot cast 'long' to 'Character'">(Character) 1 as long</error>
    <error descr="Cannot cast 'float' to 'Character'">(Character) 1 as float</error>
    <error descr="Cannot cast 'double' to 'Character'">(Character) 1 as double</error>
    <error descr="Cannot cast 'BigInteger' to 'Character'">(Character) 1 as BigInteger</error>
    <error descr="Cannot cast 'BigDecimal' to 'Character'">(Character) 1 as BigDecimal</error>
    <error descr="Cannot cast 'Void' to 'Character'">(Character) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'Character'">(Character) 1 as Boolean</error>
    <error descr="Cannot cast 'Byte' to 'Character'">(Character) 1 as Byte</error>
    <error descr="Cannot cast 'Short' to 'Character'">(Character) 1 as Short</error>
    (Character) 1 as Character
    <error descr="Cannot cast 'Integer' to 'Character'">(Character) 1 as Integer</error>
    <error descr="Cannot cast 'Long' to 'Character'">(Character) 1 as Long</error>
    <error descr="Cannot cast 'Float' to 'Character'">(Character) 1 as Float</error>
    <error descr="Cannot cast 'Double' to 'Character'">(Character) 1 as Double</error>
    (Character) 1 as Object
  }

  def castToInt() {
    (Integer) null
    <error descr="Cannot cast 'void' to 'Integer'">(Integer) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'Integer'">(Integer) 1 as boolean</error>
    (Integer) 1 as byte
    (Integer) 1 as short
    (Integer) 1 as char
    (Integer) 1 as int
    (Integer) 1 as long
    (Integer) 1 as float
    (Integer) 1 as double
    (Integer) 1 as BigInteger
    (Integer) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'Integer'">(Integer) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'Integer'">(Integer) 1 as Boolean</error>
    (Integer) 1 as Byte
    (Integer) 1 as Short
    <error descr="Cannot cast 'Character' to 'Integer'">(Integer) 1 as Character</error>
    (Integer) 1 as Integer
    (Integer) 1 as Long
    (Integer) 1 as Float
    (Integer) 1 as Double
    (Integer) 1 as Object
  }

  def castToLong() {
    (Long) null
    <error descr="Cannot cast 'void' to 'Long'">(Long) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'Long'">(Long) 1 as boolean</error>
    (Long) 1 as byte
    (Long) 1 as short
    (Long) 1 as char
    (Long) 1 as int
    (Long) 1 as long
    (Long) 1 as float
    (Long) 1 as double
    (Long) 1 as BigInteger
    (Long) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'Long'">(Long) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'Long'">(Long) 1 as Boolean</error>
    (Long) 1 as Byte
    (Long) 1 as Short
    <error descr="Cannot cast 'Character' to 'Long'">(Long) 1 as Character</error>
    (Long) 1 as Integer
    (Long) 1 as Long
    (Long) 1 as Float
    (Long) 1 as Double
    (Long) 1 as Object
  }

  def castToFloat() {
    (Float) null
    <error descr="Cannot cast 'void' to 'Float'">(Float) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'Float'">(Float) 1 as boolean</error>
    (Float) 1 as byte
    (Float) 1 as short
    (Float) 1 as char
    (Float) 1 as int
    (Float) 1 as long
    (Float) 1 as float
    (Float) 1 as double
    (Float) 1 as BigInteger
    (Float) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'Float'">(Float) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'Float'">(Float) 1 as Boolean</error>
    (Float) 1 as Byte
    (Float) 1 as Short
    <error descr="Cannot cast 'Character' to 'Float'">(Float) 1 as Character</error>
    (Float) 1 as Integer
    (Float) 1 as Long
    (Float) 1 as Float
    (Float) 1 as Double
    (Float) 1 as Object
  }

  def castToDouble() {
    (Double) null
    <error descr="Cannot cast 'void' to 'Double'">(Double) 1 as void</error>
    <error descr="Cannot cast 'boolean' to 'Double'">(Double) 1 as boolean</error>
    (Double) 1 as byte
    (Double) 1 as short
    (Double) 1 as char
    (Double) 1 as int
    (Double) 1 as long
    (Double) 1 as float
    (Double) 1 as double
    (Double) 1 as BigInteger
    (Double) 1 as BigDecimal
    <error descr="Cannot cast 'Void' to 'Double'">(Double) 1 as Void</error>
    <error descr="Cannot cast 'Boolean' to 'Double'">(Double) 1 as Boolean</error>
    (Double) 1 as Byte
    (Double) 1 as Short
    <error descr="Cannot cast 'Character' to 'Double'">(Double) 1 as Character</error>
    (Double) 1 as Integer
    (Double) 1 as Long
    (Double) 1 as Float
    (Double) 1 as Double
    (Double) 1 as Object
  }
}
