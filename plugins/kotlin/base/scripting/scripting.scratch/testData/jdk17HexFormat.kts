// NO_MODULE
// SELECTED_JDK_FROM_PROJECT
val bytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
val hex = java.util.HexFormat.of().formatHex(bytes)
println(hex)
