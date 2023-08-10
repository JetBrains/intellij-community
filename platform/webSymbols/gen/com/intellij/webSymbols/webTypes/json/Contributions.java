
package com.intellij.webSymbols.webTypes.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Symbol can be contributed to one of the 3 namespaces - HTML, CSS and JS. Within a particular namespace there can be different kinds of symbols. In each of the namespaces, there are several predefined kinds, which integrate directly with IDE, but providers are free to define their own.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "html",
    "css",
    "js"
})
public class Contributions {

    /**
     * Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)
     * 
     */
    @JsonProperty("html")
    @JsonPropertyDescription("Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)")
    private Html html;
    /**
     * Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are6 predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.
     * 
     */
    @JsonProperty("css")
    @JsonPropertyDescription("Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are6 predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.")
    private Css css;
    /**
     * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events, but only events can be contributed globally.
     * 
     */
    @JsonProperty("js")
    @JsonPropertyDescription("Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events, but only events can be contributed globally.")
    private JsGlobal js;

    /**
     * Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)
     * 
     */
    @JsonProperty("html")
    public Html getHtml() {
        return html;
    }

    /**
     * Contains contributions to HTML namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - HTML elements and HTML attributes. There are also 2 deprecated kinds: tags (which is equivalent to 'elements') and 'events' (which was moved to JS namespace)
     * 
     */
    @JsonProperty("html")
    public void setHtml(Html html) {
        this.html = html;
    }

    /**
     * Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are6 predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.
     * 
     */
    @JsonProperty("css")
    public Css getCss() {
        return css;
    }

    /**
     * Contains contributions to CSS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are6 predefined kinds, which integrate directly with IDE - properties, classes, functions, pseudo-elements, pseudo-classes and parts.
     * 
     */
    @JsonProperty("css")
    public void setCss(Css css) {
        this.css = css;
    }

    /**
     * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events, but only events can be contributed globally.
     * 
     */
    @JsonProperty("js")
    public JsGlobal getJs() {
        return js;
    }

    /**
     * Contains contributions to JS namespace. It's property names represent symbol kinds, its property values contain list of contributions of particular kind. There are 2 predefined kinds, which integrate directly with IDE - properties and events, but only events can be contributed globally.
     * 
     */
    @JsonProperty("js")
    public void setJs(JsGlobal js) {
        this.js = js;
    }

}
