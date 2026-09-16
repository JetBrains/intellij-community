package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.waitFor
import javax.swing.JTextField
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.endpointsToolWindow(action: EndpointsToolWindowUi.() -> Unit = {}): EndpointsToolWindowUi =
  x(EndpointsToolWindowUi::class.java, "'Endpoints' tool window") {
    componentWithChild(
      byClass("InternalDecoratorImpl"),
      byAccessibleName(EndpointsToolWindowUi.TOOL_WINDOW_ID)
    )
  }.apply(action)

class EndpointsToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  /** The list of the endpoints of the project. */
  val endpointsList: JListUiComponent = x(JListUiComponent::class.java, "endpoints list") { byType(ENDPOINTS_LIST_TYPE) }

  /** The field which filters the list of the endpoints. */
  val filterField: JTextFieldUI =
    x("endpoints filter") { byType(FILTER_FIELD_TYPE) }.textField("endpoints filter field") { byType(JTextField::class.java) }

  /** The panel which holds the filter field and the list of the endpoints. */
  private val endpointsView: UiComponent = x("endpoints view") { byType(ENDPOINTS_VIEW_TYPE) }

  /**
   * Sets the filter query, applies it, and waits until the list holds each of [expected].
   * An empty query shows every endpoint of the project.
   *
   * The panel sets the text and reloads the list in one EDT action, so it needs no key event.
   * A key route loses the query. The field opens a completion popup 250 ms after each change of
   * the text. The popup then takes the ENTER key, and it rewrites the text of the field.
   */
  fun filterBy(query: String, expected: List<String>, timeout: Duration = 2.minutes) {
    step("Filter the endpoints by '$query'") {
      val view = endpointsView.component
      driver.withContext(OnDispatcher.EDT) {
        cast(view, EndpointsViewRef::class).showEndpoints(query)
      }
      waitFor(message = "the filter field holds '$query'",
              timeout = 30.seconds,
              getter = { filterField.text },
              checker = { it == query })
      waitEndpoints(expected, timeout)
    }
  }

  /**
   * Selects the endpoint which contains [endpoint] in its text.
   *
   * The click goes through the list, because the editor shows the same path in a string literal
   * and in an inlay hint. A text search over the whole frame can therefore click outside the list.
   */
  fun selectEndpoint(endpoint: String, timeout: Duration = 30.seconds) {
    step("Select the endpoint '$endpoint'") {
      waitEndpoints(listOf(endpoint), timeout)
      endpointsList.clickItem(endpoint, fullMatch = false)
      waitFor(message = "the endpoint '$endpoint' is selected",
              timeout = timeout,
              errorMessage = { "the selected endpoints are ${endpointsList.selectedItems}" }) {
        endpointsList.selectedItems.any { it.contains(endpoint) }
      }
    }
  }

  /**
   * Waits until the list of the endpoints contains each of [endpoints].
   *
   * The list holds one [LOADING_ITEM_TEXT] item while it loads. The check therefore also waits
   * until that item is gone, because a loaded list can still miss an endpoint.
   */
  fun waitEndpoints(endpoints: List<String>, timeout: Duration = 1.minutes) {
    step("Check that the endpoints $endpoints are displayed") {
      endpointsList.waitFound(timeout)
      waitFor(message = "the endpoints $endpoints are displayed",
              timeout = timeout,
              errorMessage = { items ->
                if (items.any { it.contains(LOADING_ITEM_TEXT) }) "the endpoints list still shows '$LOADING_ITEM_TEXT': $items"
                else "${endpoints.filterNot { endpoint -> items.any { it.contains(endpoint) } }} not found in $items"
              },
              getter = { endpointsList.items },
              checker = { items ->
                items.none { it.contains(LOADING_ITEM_TEXT) } &&
                endpoints.all { endpoint -> items.any { it.contains(endpoint) } }
              })
    }
  }

  companion object {
    const val TOOL_WINDOW_ID: String = "Endpoints"

    private const val ENDPOINTS_VIEW_TYPE: String = "com.intellij.microservices.backend.flat.EndpointsView"
    private const val ENDPOINTS_LIST_TYPE: String = "com.intellij.microservices.backend.flat.EndpointsList"
    private const val FILTER_FIELD_TYPE: String = "com.intellij.ui.filterField.FilterSearchTextField"
    private const val LOADING_ITEM_TEXT: String = "Loading endpoints"
  }
}

@Remote("com.intellij.microservices.backend.flat.EndpointsView",
        plugin = "com.intellij.microservices.ui/intellij.microservices.backend")
private interface EndpointsViewRef {
  /** Sets the filter query and reloads the list of the endpoints. */
  fun showEndpoints(searchedValue: String)
}
