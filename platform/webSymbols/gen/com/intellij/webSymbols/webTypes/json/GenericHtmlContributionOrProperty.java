
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = GenericHtmlContributionOrProperty.MyDeserializer.class)
public class GenericHtmlContributionOrProperty
    implements GenericContributionOrProperty
{

    /**
     * Type: {@code GenericHtmlContribution | String | Double | Boolean}
     * 
     */
    private Object value;

    /**
     * Type: {@code GenericHtmlContribution | String | Double | Boolean}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code GenericHtmlContribution | String | Double | Boolean}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<GenericHtmlContributionOrProperty>
    {


        @Override
        public GenericHtmlContributionOrProperty deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            GenericHtmlContributionOrProperty result = new GenericHtmlContributionOrProperty();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                result.value = parser.readValueAs(GenericHtmlContribution.class);
            } else {
                if (token == JsonToken.VALUE_STRING) {
                    result.value = parser.readValueAs(String.class);
                } else {
                    if ((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT)) {
                        result.value = parser.readValueAs(Double.class);
                    } else {
                        if ((token == JsonToken.VALUE_TRUE)||(token == JsonToken.VALUE_FALSE)) {
                            result.value = parser.readValueAs(Boolean.class);
                        } else {
                            deserializationContext.handleUnexpectedToken(Object.class, parser);
                        }
                    }
                }
            }
            return result;
        }

    }

}
