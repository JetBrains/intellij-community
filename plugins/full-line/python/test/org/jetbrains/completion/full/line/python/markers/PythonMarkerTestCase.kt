package org.jetbrains.completion.full.line.python.markers

import com.jetbrains.python.PythonLanguage
import org.jetbrains.completion.full.line.markers.Marker
import org.jetbrains.completion.full.line.markers.MarkerTestCase
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class PythonMarkerTestCase : MarkerTestCase(PythonLanguage.INSTANCE) {
  @ParameterizedTest(name = "{0}")
  @MethodSource("pythonMarkers")
  @EnabledIfEnvironmentVariable(
    named = "FLCC_MARKERS_TOKEN",
    matches = "^(?!\\s*\$).+",
    disabledReason = "Full line marker's token was not provided"
  )
  fun `test python markers with local model and plugin`(marker: Marker) = wrapJUnit3TestCase {
    initModel()
    doTestMarkers(marker)
  }

  companion object {
    @JvmStatic
    fun pythonMarkers() = markers("python")
  }
}
