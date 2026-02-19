<?php

$time = time();

$config = <<<CONFIG
{
  "productCode": "IC",
  "versions": [
    {
      "majorBuildVersionBorders": {
        "from": "193.1"
      },
      "releaseFilters": [
        {
          "releaseType": "ALL",
          "from": 0,
          "to": 256
        }
      ],
      "endpoints": {
        "send": "http://test-send-url",
        "metadata": "metadata/$time/"
      },
      "options": {
        "dataThreshold": 16000,
        "groupDataThreshold": 8000,
        "groupAlertThreshold": 4000,
        "id_salt": "test_salt",
        "id_salt_revision": 1
      }
    }
  ]
}
CONFIG;

echo $config;