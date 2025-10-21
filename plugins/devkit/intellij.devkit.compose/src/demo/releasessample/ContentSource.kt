package com.intellij.devkit.compose.demo.releasessample

import kotlinx.datetime.LocalDate

internal abstract class ContentSource<T : ContentItem> {
  abstract val items: List<T>

  abstract val displayName: String

  fun isSameAs(other: ContentSource<*>): Boolean {
    val thisComparable = getComparableSource()
    val otherComparable = other.getComparableSource()

    return thisComparable == otherComparable
  }

  private fun getComparableSource() =
    when (this) {
      is FilteredContentSource<*> -> original
      else -> this
    }
}

internal data class FilteredContentSource<T : ContentItem>(
  override val items: List<T>,
  val original: ContentSource<*>,
) : ContentSource<T>() {
  override val displayName: String
    get() = original.displayName
}

internal object AndroidReleases : ContentSource<ContentItem.AndroidRelease>() {
  override val items =
    listOf(
      ContentItem.AndroidRelease(
        displayText = "Android 1.0",
        imagePath = null,
        versionName = "1.0",
        codename = null,
        apiLevel = 1,
        releaseDate = LocalDate(2008, 9, 23),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 1.1",
        imagePath = null,
        versionName = "1.1",
        codename = "Petit Four",
        apiLevel = 2,
        releaseDate = LocalDate(2009, 2, 9),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Cupcake",
        imagePath = "/android-releases/cupcake.png",
        versionName = "1.5",
        codename = "Cupcake",
        apiLevel = 3,
        releaseDate = LocalDate(2009, 4, 27),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Donut",
        imagePath = "/android-releases/donut.png",
        versionName = "1.6",
        codename = "Donut",
        apiLevel = 4,
        releaseDate = LocalDate(2009, 9, 15),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Eclair (2.0)",
        imagePath = "/android-releases/eclair.png",
        versionName = "2.0",
        codename = "Eclair",
        apiLevel = 5,
        releaseDate = LocalDate(2009, 10, 27),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Eclair (2.0.1)",
        imagePath = "/android-releases/eclair.png",
        versionName = "2.0.1",
        codename = "Eclair",
        apiLevel = 6,
        releaseDate = LocalDate(2009, 12, 3),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Eclair (2.1)",
        imagePath = "/android-releases/eclair.png",
        versionName = "2.1",
        codename = "Eclair",
        apiLevel = 7,
        releaseDate = LocalDate(2010, 1, 11),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Froyo",
        imagePath = "/android-releases/froyo.png",
        versionName = "2.2 – 2.2.3",
        codename = "Froyo",
        apiLevel = 8,
        releaseDate = LocalDate(2010, 5, 20),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Gingerbread (2.3 – 2.3.2)",
        imagePath = "/android-releases/gingerbread.png",
        versionName = "2.3 – 2.3.2",
        codename = "Gingerbread",
        apiLevel = 9,
        releaseDate = LocalDate(2010, 12, 6),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Gingerbread (2.3.3 – 2.3.7)",
        imagePath = "/android-releases/gingerbread.png",
        versionName = "2.3.3 – 2.3.7",
        codename = "Gingerbread",
        apiLevel = 10,
        releaseDate = LocalDate(2011, 2, 9),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Honeycomb (3.0)",
        imagePath = "/android-releases/honeycomb.svg",
        versionName = "3.0",
        codename = "Honeycomb",
        apiLevel = 11,
        releaseDate = LocalDate(2011, 2, 22),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Honeycomb (3.1)",
        imagePath = "/android-releases/honeycomb.svg",
        versionName = "3.1",
        codename = "Honeycomb",
        apiLevel = 12,
        releaseDate = LocalDate(2011, 5, 10),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Honeycomb (3.2 – 3.2.6)",
        imagePath = "/android-releases/honeycomb.svg",
        versionName = "3.2 – 3.2.6",
        codename = "Honeycomb",
        apiLevel = 13,
        releaseDate = LocalDate(2011, 7, 15),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Ice Cream Sandwich (4.0 – 4.0.2)",
        imagePath = "/android-releases/ice-cream-sandwich.svg",
        versionName = "4.0 – 4.0.2",
        codename = "Ice Cream Sandwich",
        apiLevel = 14,
        releaseDate = LocalDate(2011, 10, 18),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Ice Cream Sandwich (4.0.3 – 4.0.4)",
        imagePath = "/android-releases/ice-cream-sandwich.svg",
        versionName = "4.0.3 – 4.0.4",
        codename = "Ice Cream Sandwich",
        apiLevel = 15,
        releaseDate = LocalDate(2011, 12, 16),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Jelly Bean (4.1 – 4.1.2)",
        imagePath = "/android-releases/jelly-bean.svg",
        versionName = "4.1 – 4.1.2",
        codename = "Jelly Bean",
        apiLevel = 16,
        releaseDate = LocalDate(2012, 7, 9),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Jelly Bean (4.2 – 4.2.2)",
        imagePath = "/android-releases/jelly-bean.svg",
        versionName = "4.2 – 4.2.2",
        codename = "Jelly Bean",
        apiLevel = 17,
        releaseDate = LocalDate(2012, 11, 13),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Jelly Bean (4.3 – 4.3.1)",
        imagePath = "/android-releases/jelly-bean.svg",
        versionName = "4.3 – 4.3.1",
        codename = "Jelly Bean",
        apiLevel = 18,
        releaseDate = LocalDate(2013, 7, 24),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android KitKat (4.4 – 4.4.4)",
        imagePath = "/android-releases/kitkat.svg",
        versionName = "4.4 – 4.4.4",
        codename = "Key Lime Pie",
        apiLevel = 19,
        releaseDate = LocalDate(2013, 10, 31),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android KitKat (4.4W – 4.4W.2)",
        imagePath = "/android-releases/kitkat.svg",
        versionName = "4.4W – 4.4W.2",
        codename = "Key Lime Pie",
        apiLevel = 20,
        releaseDate = LocalDate(2014, 6, 25),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Lollipop (5.0 – 5.0.2)",
        imagePath = "/android-releases/lollipop.svg",
        versionName = "5.0 – 5.0.2",
        codename = "Lemon Meringue Pie",
        apiLevel = 21,
        releaseDate = LocalDate(2014, 10, 4),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Lollipop (5.1 – 5.1.1)",
        imagePath = "/android-releases/lollipop.svg",
        versionName = "5.1 – 5.1.1",
        codename = "Lemon Meringue Pie",
        apiLevel = 22,
        releaseDate = LocalDate(2015, 3, 2),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Marshmallow",
        imagePath = "/android-releases/marshmallow.svg",
        versionName = "6.0 – 6.0.1",
        codename = "Macadamia Nut Cookie",
        apiLevel = 23,
        releaseDate = LocalDate(2015, 10, 2),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Nougat (7.0)",
        imagePath = "/android-releases/nougat.svg",
        versionName = "7.0",
        codename = "New York Cheesecake",
        apiLevel = 24,
        releaseDate = LocalDate(2016, 8, 22),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Nougat (7.1 – 7.1.2)",
        imagePath = "/android-releases/nougat.svg",
        versionName = "7.1 – 7.1.2",
        codename = "New York Cheesecake",
        apiLevel = 25,
        releaseDate = LocalDate(2016, 10, 4),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Oreo (8.0)",
        imagePath = "/android-releases/oreo.svg",
        versionName = "8.0",
        codename = "Oatmeal Cookie",
        apiLevel = 26,
        releaseDate = LocalDate(2017, 8, 21),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Oreo (8.1)",
        imagePath = "/android-releases/oreo.svg",
        versionName = "8.1",
        codename = "Oatmeal Cookie",
        apiLevel = 27,
        releaseDate = LocalDate(2017, 12, 5),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android Pie",
        imagePath = "/android-releases/pie.svg",
        versionName = "9",
        codename = "Pistachio Ice Cream",
        apiLevel = 28,
        releaseDate = LocalDate(2018, 8, 6),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 10",
        imagePath = "/android-releases/10.svg",
        versionName = "10",
        codename = "Quince Tart",
        apiLevel = 29,
        releaseDate = LocalDate(2019, 9, 3),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 11",
        imagePath = "/android-releases/11.svg",
        versionName = "11",
        codename = "Red Velvet Cake",
        apiLevel = 30,
        releaseDate = LocalDate(2020, 9, 8),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 12",
        imagePath = "/android-releases/12.svg",
        versionName = "12",
        codename = "Snow Cone",
        apiLevel = 31,
        releaseDate = LocalDate(2021, 10, 4),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 12L",
        imagePath = "/android-releases/12.svg",
        versionName = "12.1",
        codename = "Snow Cone v2",
        apiLevel = 32,
        releaseDate = LocalDate(2022, 3, 7),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 13",
        imagePath = "/android-releases/13.svg",
        versionName = "13",
        codename = "Tiramisu",
        apiLevel = 33,
        releaseDate = LocalDate(2022, 8, 15),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 14",
        imagePath = "/android-releases/14.svg",
        versionName = "14",
        codename = "Upside Down Cake",
        apiLevel = 34,
        releaseDate = LocalDate(2023, 10, 4),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 15",
        imagePath = "/android-releases/15.svg",
        versionName = "15",
        codename = "Vanilla Ice Cream",
        apiLevel = 35,
        releaseDate = LocalDate(2024, 2, 17),
      ),
      ContentItem.AndroidRelease(
        displayText = "Android 16",
        imagePath = "/android-releases/16.svg",
        versionName = "16",
        codename = "Baklava",
        apiLevel = 36,
        releaseDate = LocalDate(2025, 1, 23),
      ),
    )

  override val displayName = "Android releases"
}
