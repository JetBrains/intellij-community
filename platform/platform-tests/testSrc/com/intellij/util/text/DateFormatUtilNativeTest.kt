package com.intellij.util.text

import com.intellij.testFramework.junit5.TestApplication
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.MAC
import org.junit.jupiter.api.condition.OS.WINDOWS
import java.lang.foreign.MemorySegment
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class DateFormatUtilNativeTest {
  @BeforeEach
  fun checkClassLoader() {
    assertThat(DateFormatUtil::class.java.classLoader).isSameAs(javaClass.classLoader)
  }

  @Test
  fun `CoreFoundation ranges contain two 64-bit indexes`() {
    assertThat(CFRange().size()).isEqualTo(16)
  }

  @Test
  @EnabledOnOs(MAC)
  fun `macOS formats match JNA`() {
    val library = Native.load("CoreFoundation", CoreFoundation::class.java)
    val nativeLocale = library.CFLocaleCopyCurrent()
    assertThat(nativeLocale).isNotNull()
    try {
      val identifier = readString(library, library.CFLocaleGetIdentifier(nativeLocale))
        .substringBefore('.').substringBefore('@')
      val separator = identifier.indexOf('_')
      val locale = if (separator < 0) Locale.of(identifier)
      else Locale.of(identifier.substring(0, separator), identifier.substring(separator + 1))
      val styles = listOf(1L to 0L, 0L to 1L, 0L to 2L, 1L to 1L)
      val patterns = styles.map { (dateStyle, timeStyle) ->
        val formatter = library.CFDateFormatterCreate(null, nativeLocale, dateStyle, timeStyle)
        assertThat(formatter).isNotNull()
        try {
          readString(library, library.CFDateFormatterGetFormat(formatter))
        }
        finally {
          library.CFRelease(formatter)
        }
      }
      repeat(3) {
        assertFormats(DateFormatUtil.getMacFormats(), patterns, locale)
      }
    }
    finally {
      library.CFRelease(nativeLocale)
    }
  }

  @Test
  @EnabledOnOs(MAC)
  fun `CoreFoundation strings preserve UTF-16 characters`() {
    val library = Native.load("CoreFoundation", CoreFoundation::class.java)
    for (text in listOf("", "yyyy\u5E74MM\u6708dd\u65E5", "h:mm\u202Fa", "\u00C9t\u00E9 \uD83D\uDDD3\uFE0F", "before\u0000after")) {
      val string = library.CFStringCreateWithCharacters(null, text.toCharArray(), text.length.toLong())
      assertThat(string).isNotNull()
      try {
        assertThat(DateFormatUtil.getMacString(MemorySegment.ofAddress(Pointer.nativeValue(string)))).isEqualTo(text)
      }
      finally {
        library.CFRelease(string)
      }
    }
  }

  @Test
  fun `a null CoreFoundation string is rejected before a native call`() {
    assertThrows<IllegalStateException> {
      DateFormatUtil.getMacString(MemorySegment.NULL)
    }
  }

  @Test
  @EnabledOnOs(WINDOWS)
  fun `Windows formats match JNA`() {
    val library = Native.load("kernel32", WindowsLocale::class.java)
    val patterns = listOf(0x0000001F, 0x00000079, 0x00001003).map { localeType ->
      val buffer = CharArray(128)
      val count = library.GetLocaleInfoEx(null, localeType, buffer, buffer.size)
      assertThat(count).isBetween(2, buffer.size)
      String(buffer, 0, count - 1)
        .replace('g', 'G').replace("dddd", "EEEE").replace("ddd", "E").replace("tt", "a").replace("t", "a")
    }
    assertFormats(DateFormatUtil.getWindowsFormats(), patterns + "${patterns[0]} ${patterns[1]}",
                  Locale.getDefault(Locale.Category.FORMAT))
  }

  private fun assertFormats(formats: DateTimeFormatManager.Formats, patterns: List<String>, locale: Locale) {
    val instant = LocalDateTime.of(2026, 9, 8, 13, 45, 27).atZone(ZoneId.systemDefault())
    val actual = listOf(formats.date(), formats.timeShort(), formats.timeMedium(), formats.dateTime())
    for ((formatter, pattern) in actual.zip(patterns)) {
      assertThat(formatter.locale).isEqualTo(locale)
      assertThat(formatter.format(instant)).isEqualTo(DateTimeFormatter.ofPattern(pattern.trim(), locale).format(instant))
    }
    val date = Date.from(instant.toInstant())
    assertThat(formats.dateFmt().format(date)).isEqualTo(SimpleDateFormat(patterns[0]).format(date))
    assertThat(formats.dateTimeFmt().format(date)).isEqualTo(SimpleDateFormat(patterns[3]).format(date))
  }

  private fun readString(library: CoreFoundation, string: Pointer): String {
    val length = Math.toIntExact(library.CFStringGetLength(string))
    if (length == 0) return ""
    val buffer = CharArray(length)
    library.CFStringGetCharacters(string, CFRange(0, length.toLong()), buffer)
    return String(buffer)
  }

  @Suppress("TestFunctionName")
  interface CoreFoundation : Library {
    fun CFLocaleCopyCurrent(): Pointer
    fun CFLocaleGetIdentifier(locale: Pointer): Pointer
    fun CFDateFormatterCreate(allocator: Pointer?, locale: Pointer, dateStyle: Long, timeStyle: Long): Pointer
    fun CFDateFormatterGetFormat(formatter: Pointer): Pointer
    fun CFStringCreateWithCharacters(allocator: Pointer?, characters: CharArray, count: Long): Pointer
    fun CFStringGetLength(string: Pointer): Long
    fun CFStringGetCharacters(string: Pointer, range: CFRange, buffer: CharArray)
    fun CFRelease(reference: Pointer)
  }

  @Structure.FieldOrder("location", "length")
  class CFRange(@JvmField var location: Long = 0, @JvmField var length: Long = 0) : Structure(), Structure.ByValue

  @Suppress("TestFunctionName")
  interface WindowsLocale : Library {
    fun GetLocaleInfoEx(localeName: WString?, localeType: Int, buffer: CharArray, capacity: Int): Int
  }
}
