
package com.intellij.webSymbols.webTypes.json;

import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = GenericHtmlContributions.MyDeserializer.class)
public class GenericHtmlContributions
    extends ArrayList<GenericHtmlContributionOrProperty>
{


    public static class MyDeserializer
        extends JsonDeserializer<GenericHtmlContributions>
    {


        @Override
        public GenericHtmlContributions deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            GenericHtmlContributions result = new GenericHtmlContributions();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken()!= JsonToken.END_ARRAY) {
                    token = parser.currentToken();
                    if ((((((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT))||(token == JsonToken.VALUE_TRUE))||(token == JsonToken.VALUE_FALSE))||(token == JsonToken.VALUE_STRING))||(token == JsonToken.START_OBJECT)) {
                        result.add(parser.readValueAs(GenericHtmlContributionOrProperty.class));
                    } else {
                        parser.readValueAsTree();
                    }
                }
            } else {
                if ((((((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT))||(token == JsonToken.VALUE_TRUE))||(token == JsonToken.VALUE_FALSE))||(token == JsonToken.VALUE_STRING))||(token == JsonToken.START_OBJECT)) {
                    result.add(parser.readValueAs(GenericHtmlContributionOrProperty.class));
                } else {
                    parser.readValueAsTree();
                }
            }
            return result;
        }

    }

}
