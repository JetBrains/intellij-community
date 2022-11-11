import abc
from typing import List, Type, Optional

from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase


class LicenseFilterBase(ProcessorBase):
    _SUITABLE_LICENSES = {
        "MIT License",
        "Apache License 2.0",
        'BSD 3-Clause "New" or "Revised" License',
        "Do What The F*ck You Want To Public License",
    }

    @abc.abstractmethod
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        raise NotImplementedError

    @staticmethod
    def is_deterministic() -> bool:
        return True

    @staticmethod
    def is_slow() -> bool:
        return False

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        return []

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        return []


class LicenseFilterTrainData(LicenseFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp if dp.license in self._SUITABLE_LICENSES else None


class LicenseFilterTrainModel(LicenseFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp if dp.license in self._SUITABLE_LICENSES else None


class LicenseFilterInference(LicenseFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp
