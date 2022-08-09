package org.jetbrains.completion.full.line.settings.ui

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.panel
import org.jetbrains.completion.full.line.providers.CloudFullLineCompletionProvider
import org.jetbrains.completion.full.line.settings.FullLineNotifications
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState.FLVerificationStatus
import org.jetbrains.completion.full.line.settings.ui.components.LoadingComponent
import org.jetbrains.completion.full.line.settings.ui.components.loadingStatus
import javax.swing.JButton

class VerifyCloudConfigurable : BoundConfigurable(message("fl.server.completion.display")) {
    private val settings = MlServerCompletionAuthState.getInstance()
    private lateinit var authTokenField: CellBuilder<JBPasswordField>

    override fun createPanel(): DialogPanel {
        return if (MLServerCompletionSettings.availableLanguages.isEmpty()) {
            noLanguagePanel()
        } else {
            verifyTokenPanel()
        }
    }

    private fun noLanguagePanel(): DialogPanel {
        return panel {
            commentRow("You have no supported languages.")
        }
    }

    private fun verifyTokenPanel(): DialogPanel {
        return panel {
            row {
                row {
                    cell {
                        label(message("fl.server.completion.auth")).comment(message("fl.server.eap.comment"))
                        authTokenField = component(JBPasswordField().apply {
                            text = settings.state.authToken
                            columns = 25
                        }).withPasswordBinding(settings.state::authToken)
                            .onApply { FullLineNotifications.Cloud.clearAuthError() }
                    }
                }
                row {
                    subRowIndent = 1
                    cell {
                        val loadingIcon = LoadingComponent()
                        val button = JButton(message("fl.server.eap.verify"))
                        button.putClientProperty(AUTH_TOKEN_UPDATE, button.getClientProperty(AUTH_TOKEN_UPDATE) ?: false)

                        button.addActionListener {
                            loadingIcon.withAsyncProgress(
                                CloudFullLineCompletionProvider.checkStatus(
                                    MLServerCompletionSettings.availableLanguages.first(),
                                    String(authTokenField.component.password)
                                ).then {
                                    val state = if (it == 200) {
                                        LoadingComponent.State.SUCCESS.also { settings.state.verified = FLVerificationStatus.VERIFIED }
                                    } else {
                                        LoadingComponent.State.ERROR.also { settings.state.verified = FLVerificationStatus.UNVERIFIED }
                                    }
                                    val msg = when (it) {
                                        200 -> "Successful"
                                        403 -> "Auth key is incorrect"
                                        else -> "Status failed with code $this"
                                    }
                                    button.putClientProperty(
                                        AUTH_TOKEN_UPDATE,
                                        !(button.getClientProperty(AUTH_TOKEN_UPDATE) as Boolean)
                                    )
                                    state to msg
                                }
                            )
                        }
                        if (settings.isVerified()) {
                            button.doClick()
                        }
                        component(button)
                        loadingStatus(loadingIcon)
                    }
                }
            }
        }
    }

    override fun getHelpTopic() = "full.line.completion.verify"
}
